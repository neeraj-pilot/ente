use ente_core::crypto::{self, Header, Key, Nonce, SecretVec, blob, secretbox};
use ente_core::http::{self, Api, RequestBuilder};
use ente_core::{Session, b64};
use serde::{Deserialize, Serialize};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    Http(#[from] http::Error),
    #[error(transparent)]
    Crypto(#[from] crypto::Error),
    #[error(transparent)]
    Base64(#[from] b64::DecodeError),
    #[error("invalid entity diff: {0}")]
    InvalidDiff(&'static str),
}

#[derive(Debug, thiserror::Error)]
pub enum KeyError {
    #[error(transparent)]
    Crypto(#[from] crypto::Error),
    #[error(transparent)]
    Base64(#[from] b64::DecodeError),
}

#[derive(Clone, Copy, Debug)]
pub enum Endpoint {
    UserEntity(&'static str),
    Authenticator,
}

impl Endpoint {
    fn get(self, api: &Api, path: &str) -> RequestBuilder {
        match self {
            Self::UserEntity(entity_type) => api
                .get(&format!("/user-entity{path}"))
                .query(&[("type", entity_type)]),
            Self::Authenticator => api.get(&format!("/authenticator{path}")),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WrappedKey {
    pub encrypted_key: String,
    pub header: String,
}

impl WrappedKey {
    fn seal(key: &Key, master_key: &Key) -> Self {
        let sealed = secretbox::encrypt(key.as_bytes(), master_key);
        Self {
            encrypted_key: b64::encode(&sealed.encrypted_data),
            header: b64::encode(sealed.nonce.as_bytes()),
        }
    }

    pub fn open(&self, master_key: &Key) -> Result<Key, KeyError> {
        let key = SecretVec::new(secretbox::decrypt(
            &b64::decode(&self.encrypted_key)?,
            &Nonce::try_from_slice(&b64::decode(&self.header)?)?,
            master_key,
        )?);
        Ok(Key::try_from(key)?)
    }
}

pub async fn fetch_key(
    session: &Session,
    endpoint: Endpoint,
) -> Result<Option<WrappedKey>, http::Error> {
    http::retry(|| async {
        let response = endpoint.get(&session.api, "/key").send().await?;
        if response.status() == 404 {
            return Ok(None);
        }
        response.error_for_code().await?.json().await.map(Some)
    })
    .await
}

pub async fn ensure_key(
    session: &Session,
    entity_type: &'static str,
) -> Result<WrappedKey, http::Error> {
    #[derive(Serialize)]
    #[serde(rename_all = "camelCase")]
    struct Request<'a> {
        r#type: &'a str,
        encrypted_key: &'a str,
        header: &'a str,
    }
    let wrapped = WrappedKey::seal(&Key::generate(), &session.master_key);
    let request = Request {
        r#type: entity_type,
        encrypted_key: &wrapped.encrypted_key,
        header: &wrapped.header,
    };
    http::retry(|| async {
        session
            .api
            .post("/user-entity/key/ensure")
            .json(&request)
            .send()
            .await?
            .error_for_code()
            .await?
            .json()
            .await
    })
    .await
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Entity {
    pub id: String,
    pub updated_at: i64,
    pub is_deleted: bool,
    pub encrypted_data: Option<String>,
    pub header: Option<String>,
}

impl Entity {
    pub fn decrypt(&self, key: &Key) -> Result<Vec<u8>, Error> {
        let (Some(data), Some(header)) = (&self.encrypted_data, &self.header) else {
            return Err(Error::InvalidDiff("entity has no encrypted data"));
        };
        Ok(blob::decrypt(
            &b64::decode(data)?,
            &Header::try_from_slice(&b64::decode(header)?)?,
            key,
        )?)
    }
}

#[derive(Debug)]
pub struct Page {
    pub entities: Vec<Entity>,
    pub cursor: i64,
    pub has_more: bool,
}

pub async fn diff(
    session: &Session,
    endpoint: Endpoint,
    since: i64,
    limit: u16,
) -> Result<Page, Error> {
    #[derive(Deserialize)]
    struct Response {
        diff: Vec<Entity>,
    }
    let response: Response = http::retry(|| async {
        endpoint
            .get(&session.api, "/entity/diff")
            .query(&[("sinceTime", since)])
            .query(&[("limit", limit)])
            .send()
            .await?
            .error_for_code()
            .await?
            .json()
            .await
    })
    .await?;
    page(response.diff, since, limit)
}

fn page(entities: Vec<Entity>, since: i64, limit: u16) -> Result<Page, Error> {
    if entities.iter().any(|entity| entity.updated_at <= since) {
        return Err(Error::InvalidDiff("entity is not newer than the cursor"));
    }
    let has_more = !entities.is_empty() && entities.len() >= usize::from(limit);
    let cursor = match entities.iter().map(|entity| entity.updated_at).max() {
        None => since,
        Some(newest) if !has_more => newest,
        Some(newest) if entities.iter().all(|entity| entity.updated_at == newest) => {
            return Err(Error::InvalidDiff("a full page shares one timestamp"));
        }
        Some(newest) => newest - 1,
    };
    Ok(Page {
        entities,
        cursor,
        has_more,
    })
}

#[cfg(test)]
mod tests {
    use ente_core::crypto::SecretKey;
    use ente_core::http::{ApiConfig, Auth};
    use mockito::{Matcher, Server};
    use serde_json::json;

    use super::*;

    fn session(origin: String, master_key: Key) -> Session {
        Session::new(
            ApiConfig {
                auth: Some(Auth::User("token".into())),
                ..ApiConfig::new(origin)
            },
            1,
            master_key,
            Key::generate(),
            SecretKey::generate(),
        )
        .unwrap()
    }

    fn sealed(updated_at: i64, plaintext: &[u8], key: &Key) -> Entity {
        let encrypted = blob::encrypt(plaintext, key).unwrap();
        Entity {
            id: updated_at.to_string(),
            updated_at,
            is_deleted: false,
            encrypted_data: Some(b64::encode(&encrypted.encrypted_data)),
            header: Some(b64::encode(encrypted.decryption_header.as_bytes())),
        }
    }

    fn tombstone(updated_at: i64) -> Entity {
        Entity {
            id: updated_at.to_string(),
            updated_at,
            is_deleted: true,
            encrypted_data: None,
            header: None,
        }
    }

    fn cursor(stamps: impl IntoIterator<Item = i64>, since: i64, limit: u16) -> (i64, bool) {
        let page = page(stamps.into_iter().map(tombstone).collect(), since, limit).unwrap();
        (page.cursor, page.has_more)
    }

    #[test]
    fn a_short_page_moves_the_cursor_to_its_newest_row() {
        assert_eq!(cursor([12, 30, 20], 10, 4), (30, false));
        assert_eq!(cursor([], 42, 0), (42, false));
    }

    #[test]
    fn a_full_page_rereads_its_newest_timestamp() {
        assert_eq!(cursor([1, 2, 3, 4], 0, 4), (3, true));
        assert_eq!(cursor([1, 2, 3, 1000], 0, 4), (999, true));
    }

    #[test]
    fn rows_at_or_before_the_cursor_or_a_tied_full_page_are_invalid() {
        for (stamps, since) in [(vec![10], 10), (vec![11, 9], 10), (vec![7, 7, 7, 7], 0)] {
            let entities = stamps.into_iter().map(tombstone).collect();
            assert!(matches!(
                page(entities, since, 4),
                Err(Error::InvalidDiff(_))
            ));
        }
    }

    #[test]
    fn entities_decrypt_with_their_key_only() {
        let key = Key::generate();
        let entity = sealed(5, b"payload", &key);
        assert_eq!(entity.decrypt(&key).unwrap(), b"payload");
        assert!(matches!(
            entity.decrypt(&Key::generate()),
            Err(Error::Crypto(_))
        ));
        assert!(matches!(
            tombstone(5).decrypt(&key),
            Err(Error::InvalidDiff(_))
        ));
    }

    #[test]
    fn wrapped_keys_open_with_their_master_key_only() {
        let master_key = Key::generate();
        let key = Key::generate();
        let wrapped = WrappedKey::seal(&key, &master_key);
        assert_eq!(wrapped.open(&master_key).unwrap(), key);
        assert!(matches!(
            wrapped.open(&Key::generate()),
            Err(KeyError::Crypto(_))
        ));
    }

    #[tokio::test]
    async fn keys_are_fetched_per_endpoint_and_missing_keys_are_none() {
        let mut server = Server::new_async().await;
        let master_key = Key::generate();
        let wrapped = WrappedKey::seal(&Key::generate(), &master_key);
        let body = json!({
            "userID": 1,
            "type": "cgroup",
            "encryptedKey": wrapped.encrypted_key,
            "header": wrapped.header,
            "createdAt": 1,
        });
        let typed = server
            .mock("GET", "/user-entity/key")
            .match_query(Matcher::UrlEncoded("type".into(), "cgroup".into()))
            .match_header("x-auth-token", "token")
            .with_body(body.to_string())
            .create_async()
            .await;
        let missing = server
            .mock("GET", "/authenticator/key")
            .match_query(Matcher::Missing)
            .with_status(404)
            .create_async()
            .await;
        let session = session(server.url(), master_key);

        assert_eq!(
            fetch_key(&session, Endpoint::UserEntity("cgroup"))
                .await
                .unwrap(),
            Some(wrapped)
        );
        assert_eq!(
            fetch_key(&session, Endpoint::Authenticator).await.unwrap(),
            None
        );
        typed.assert_async().await;
        missing.assert_async().await;
    }

    #[tokio::test]
    async fn ensure_key_offers_a_new_sealed_key_and_returns_the_stored_one() {
        let mut server = Server::new_async().await;
        let master_key = Key::generate();
        let ensure = server
            .mock("POST", "/user-entity/key/ensure")
            .match_body(Matcher::PartialJson(json!({"type": "contact"})))
            .with_body_from_request(|request| request.body().unwrap().clone())
            .create_async()
            .await;
        let session = session(server.url(), master_key);

        let stored = ensure_key(&session, "contact").await.unwrap();
        stored.open(&session.master_key).unwrap();
        ensure.assert_async().await;
    }

    #[tokio::test]
    async fn diff_queries_each_endpoint_and_applies_the_cursor_rule() {
        let mut server = Server::new_async().await;
        let key = Key::generate();
        let live = sealed(9, b"x", &key);
        let rows = json!({"diff": [
            {"id": "a", "updatedAt": 6, "isDeleted": true},
            {
                "id": "b",
                "updatedAt": 9,
                "isDeleted": false,
                "encryptedData": live.encrypted_data,
                "header": live.header,
            },
        ]});
        let typed = server
            .mock("GET", "/user-entity/entity/diff")
            .match_query(Matcher::Exact("type=location&sinceTime=5&limit=2".into()))
            .with_body(rows.to_string())
            .create_async()
            .await;
        let authenticator = server
            .mock("GET", "/authenticator/entity/diff")
            .match_query(Matcher::Exact("sinceTime=5&limit=3".into()))
            .with_body(rows.to_string())
            .create_async()
            .await;
        let session = session(server.url(), Key::generate());

        let full = diff(&session, Endpoint::UserEntity("location"), 5, 2)
            .await
            .unwrap();
        assert_eq!((full.cursor, full.has_more), (8, true));
        assert_eq!(full.entities[1].decrypt(&key).unwrap(), b"x");
        let short = diff(&session, Endpoint::Authenticator, 5, 3).await.unwrap();
        assert_eq!((short.cursor, short.has_more), (9, false));
        typed.assert_async().await;
        authenticator.assert_async().await;
    }
}
