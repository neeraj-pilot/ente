//! Space entity keys.
//!
//! Entity keys are small wrapped keys the server stores per account under a
//! string type tag (for example the Space root key). These methods read and
//! write them through the generic `/user-entity/key` endpoints. Space callers
//! keep using a combined secretbox payload; this layer splits and joins the
//! nonce/header required by the generic API.

use super::AccountSpaceCtx;
use crate::error::{Result, SpaceError};
use crate::transport::{CreateEntityKeyRequest, EntityKeyPayload, EntityKeyResponse};
use ente_core::crypto::{decode_b64, encode_b64};
use ente_core::http_legacy::Error as HttpError;

const SPACE_ENTITY_KEY_HEADER_BYTES: usize = 24;

impl AccountSpaceCtx {
    pub async fn get_entity_key(&self, key_type: &str) -> Result<Option<EntityKeyPayload>> {
        let query = vec![("type", key_type.to_owned())];
        let payload = self
            .client()
            .get_json_optional::<EntityKeyResponse>("/user-entity/key", &query)
            .await?;
        payload.map(combine_entity_key_response).transpose()
    }

    pub async fn create_entity_key(
        &self,
        key_type: &str,
        payload: &EntityKeyPayload,
    ) -> Result<()> {
        let (header, encrypted_key) = split_entity_key_payload(payload)?;
        let request = CreateEntityKeyRequest {
            key_type: key_type.to_owned(),
            encrypted_key,
            header,
        };
        match self.client().post_empty("/user-entity/key", &request).await {
            Ok(_) => Ok(()),
            Err(HttpError::Http { status: 409, .. }) => Err(SpaceError::EntityKeyConflict),
            Err(err) => Err(err.into()),
        }
    }

    pub async fn ensure_entity_key(
        &self,
        key_type: &str,
        payload: &EntityKeyPayload,
    ) -> Result<EntityKeyPayload> {
        let (header, encrypted_key) = split_entity_key_payload(payload)?;
        let request = CreateEntityKeyRequest {
            key_type: key_type.to_owned(),
            encrypted_key,
            header,
        };
        let response = self
            .client()
            .post_json::<EntityKeyResponse, _>("/user-entity/key/ensure", &request)
            .await?;
        combine_entity_key_response(response)
    }
}

fn split_entity_key_payload(payload: &EntityKeyPayload) -> Result<(String, String)> {
    let combined = decode_b64(&payload.encrypted_key)?;
    if combined.len() <= SPACE_ENTITY_KEY_HEADER_BYTES {
        return Err(SpaceError::InvalidInput(
            "entity key payload is missing header".into(),
        ));
    }
    Ok((
        encode_b64(&combined[..SPACE_ENTITY_KEY_HEADER_BYTES]),
        encode_b64(&combined[SPACE_ENTITY_KEY_HEADER_BYTES..]),
    ))
}

fn combine_entity_key_response(response: EntityKeyResponse) -> Result<EntityKeyPayload> {
    let mut combined = decode_b64(&response.header)?;
    let encrypted_key = decode_b64(&response.encrypted_key)?;
    combined.extend_from_slice(&encrypted_key);
    Ok(EntityKeyPayload {
        encrypted_key: encode_b64(&combined),
    })
}
