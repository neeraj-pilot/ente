use ente_core::{b64, crypto::SecretVec};

use crate::{
    AccountSecrets, AccountsClient, AuthenticatedAccount, Error, Result,
    auth::{self, DecryptedSecrets, KeyAttributes, SrpAttributes, derive_kek, get_recovery_key},
    models::AuthResponse,
};

pub enum LoginStep {
    EmailCode,
    Password,
    SecondFactor { totp: bool, passkey: bool },
    Complete(Box<AuthenticatedAccount>),
}

enum State {
    EmailCode,
    Password(Option<AuthResponse>),
    SecondFactor {
        response: AuthResponse,
        kek: Option<SecretVec>,
    },
    Complete,
}

pub struct LoginFlow {
    email: String,
    attributes: SrpAttributes,
    state: State,
}

impl LoginFlow {
    pub async fn start(client: &AccountsClient, email: String) -> Result<(Self, LoginStep)> {
        let attributes = client.get_srp_attributes(&email).await?;
        let (state, step) = if attributes.is_email_mfa_enabled {
            client.send_otp(&email, "login").await?;
            (State::EmailCode, LoginStep::EmailCode)
        } else {
            (State::Password(None), LoginStep::Password)
        };
        Ok((
            Self {
                email,
                attributes,
                state,
            },
            step,
        ))
    }

    pub async fn submit_password(
        &mut self,
        client: &AccountsClient,
        password: &str,
    ) -> Result<LoginStep> {
        let State::Password(response) = &self.state else {
            return Err(Error::InvalidInput("Password is not expected".into()));
        };
        if let Some(response) = response {
            let kek = derive_kek(
                password,
                &self.attributes.kek_salt,
                self.attributes.mem_limit,
                self.attributes.ops_limit,
            )?;
            let account = build_authenticated_account(response.clone(), &kek)?;
            self.state = State::Complete;
            Ok(LoginStep::Complete(Box::new(account)))
        } else {
            let (response, kek) = client.login_with_srp(password, &self.attributes).await?;
            self.advance(response, Some(kek))
        }
    }

    pub async fn submit_code(&mut self, client: &AccountsClient, code: &str) -> Result<LoginStep> {
        let (response, kek) = match &self.state {
            State::EmailCode => (client.verify_email(&self.email, code, None).await?, None),
            State::SecondFactor { response, kek } => {
                let id = response.get_two_factor_session_id().ok_or_else(|| {
                    Error::InvalidInput("Authenticator code is not expected".into())
                })?;
                let verified = client.verify_totp(id, code).await?;
                (
                    verified,
                    kek.as_ref().map(|key| SecretVec::new(key.to_vec())),
                )
            }
            _ => {
                return Err(Error::InvalidInput(
                    "Verification code is not expected".into(),
                ));
            }
        };
        self.advance(response, kek)
    }

    pub async fn resend_code(&self, client: &AccountsClient) -> Result<()> {
        if !matches!(self.state, State::EmailCode) {
            return Err(Error::InvalidInput("Email code is not expected".into()));
        }
        client.send_otp(&self.email, "login").await
    }

    pub fn passkey_url(&self, client: &AccountsClient, redirect: &str) -> Result<String> {
        let response = self.passkey_response()?;
        Ok(build_passkey_verification_url(
            response
                .accounts_url
                .as_deref()
                .ok_or(Error::MissingField("accountsUrl"))?,
            response
                .passkey_session_id
                .as_deref()
                .ok_or(Error::MissingField("passkeySessionID"))?,
            client.client_package(),
            redirect,
            None,
        ))
    }

    pub async fn poll_passkey(&mut self, client: &AccountsClient) -> Result<Option<LoginStep>> {
        let response = self.passkey_response()?;
        let id = response
            .passkey_session_id
            .as_deref()
            .ok_or(Error::MissingField("passkeySessionID"))?;
        let Some(verified) = client.check_passkey_status(id).await? else {
            return Ok(None);
        };
        let kek = match &self.state {
            State::SecondFactor { kek, .. } => kek.as_ref().map(|key| SecretVec::new(key.to_vec())),
            _ => None,
        };
        self.advance(verified, kek).map(Some)
    }

    fn passkey_response(&self) -> Result<&AuthResponse> {
        match &self.state {
            State::SecondFactor { response, .. } if response.is_passkey_required() => Ok(response),
            _ => Err(Error::InvalidInput("Passkey is not expected".into())),
        }
    }

    fn advance(&mut self, response: AuthResponse, kek: Option<SecretVec>) -> Result<LoginStep> {
        let totp = response.is_mfa_required();
        let passkey = response.is_passkey_required();
        if totp || passkey {
            self.state = State::SecondFactor { response, kek };
            return Ok(LoginStep::SecondFactor { totp, passkey });
        }
        if let Some(kek) = kek {
            let account = build_authenticated_account(response, &kek)?;
            self.state = State::Complete;
            Ok(LoginStep::Complete(Box::new(account)))
        } else {
            self.state = State::Password(Some(response));
            Ok(LoginStep::Password)
        }
    }
}

pub fn build_passkey_verification_url(
    accounts_url: &str,
    passkey_session_id: &str,
    client_package: &str,
    redirect: &str,
    recover: Option<&str>,
) -> String {
    let mut params = vec![
        ("clientPackage", client_package),
        ("passkeySessionID", passkey_session_id),
        ("redirect", redirect),
    ];
    if let Some(recover) = recover {
        params.push(("recover", recover));
    }

    let query = params
        .into_iter()
        .map(|(key, value)| format!("{key}={}", urlencoding::encode(value)))
        .collect::<Vec<_>>()
        .join("&");

    format!("{accounts_url}/passkeys/verify?{query}")
}

fn build_authenticated_account(
    auth_response: AuthResponse,
    kek: &[u8],
) -> Result<AuthenticatedAccount> {
    let key_attributes = auth_response
        .key_attributes
        .clone()
        .ok_or(Error::MissingKeyAttributes)?;
    let secrets = decrypt_auth_response(&auth_response, &key_attributes, kek)?;
    let public_key = b64::decode(&key_attributes.public_key)?;
    let recovery_key = get_recovery_key(&secrets.master_key, &key_attributes).ok();
    Ok(AuthenticatedAccount {
        user_id: auth_response.id,
        key_attributes,
        secrets: AccountSecrets {
            token: secrets.token.into_vec(),
            master_key: secrets.master_key.as_bytes().to_vec(),
            secret_key: secrets.secret_key.as_bytes().to_vec(),
            public_key,
        },
        recovery_key,
    })
}

fn decode_plain_token(token: &str) -> Result<SecretVec> {
    let bytes = b64::decode_url_safe(token)
        .or_else(|_| b64::decode(token))
        .map_err(|e| Error::Decode(format!("token: {e}")))?;
    Ok(SecretVec::new(bytes))
}

fn decrypt_auth_response(
    auth_response: &AuthResponse,
    key_attributes: &KeyAttributes,
    kek: &[u8],
) -> Result<DecryptedSecrets> {
    if let Some(encrypted_token) = auth_response.encrypted_token.as_deref() {
        auth::decrypt_secrets(kek, key_attributes, encrypted_token)
    } else if let Some(token) = auth_response.token.as_deref() {
        let (master_key, secret_key) = auth::decrypt_keys_only(kek, key_attributes)?;
        Ok(DecryptedSecrets {
            master_key,
            secret_key,
            token: decode_plain_token(token)?,
        })
    } else {
        Err(Error::Protocol("No token in response".into()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mockito::{Matcher, Server};
    use serde_json::json;
    use uuid::Uuid;

    use crate::{AccountsClientConfig, auth::KeyDerivationStrength};

    async fn start_email_login(
        server: &mut Server,
        attributes: &KeyAttributes,
        response: serde_json::Value,
    ) -> (AccountsClient, LoginFlow) {
        let srp = server
            .mock("GET", "/users/srp/attributes")
            .match_query(Matcher::UrlEncoded(
                "email".into(),
                "user@example.org".into(),
            ))
            .with_body(
                json!({"attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1; 16]),
                    "memLimit": attributes.mem_limit,
                    "opsLimit": attributes.ops_limit,
                    "kekSalt": attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }})
                .to_string(),
            )
            .create_async()
            .await;
        let ott = server.mock("POST", "/users/ott").create_async().await;
        server
            .mock("POST", "/users/verify-email")
            .with_body(response.to_string())
            .create_async()
            .await;
        let client = AccountsClient::new(
            AccountsClientConfig::new("io.ente.photos").with_origin(server.url()),
        )
        .unwrap();
        let (flow, step) = LoginFlow::start(&client, "user@example.org".into())
            .await
            .unwrap();
        assert!(matches!(step, LoginStep::EmailCode));
        srp.assert_async().await;
        ott.assert_async().await;
        (client, flow)
    }

    #[tokio::test]
    async fn email_login_retries_password_and_accepts_plain_token_without_recovery_key() {
        let generated =
            auth::generate_keys_with_strength("password", KeyDerivationStrength::Interactive)
                .unwrap();
        let mut attributes = generated.key_attributes;
        attributes.recovery_key_encrypted_with_master_key = None;
        attributes.recovery_key_decryption_nonce = None;
        let token = [255; 32];
        let mut server = Server::new_async().await;
        let (client, mut flow) = start_email_login(
            &mut server,
            &attributes,
            json!({"id": 77, "keyAttributes": attributes, "token": b64::encode_url_safe(&token)}),
        )
        .await;

        assert!(matches!(
            flow.submit_password(&client, "password").await,
            Err(Error::InvalidInput(_))
        ));
        assert!(matches!(
            flow.submit_code(&client, "123456").await.unwrap(),
            LoginStep::Password
        ));
        assert!(matches!(
            flow.submit_password(&client, "wrong").await,
            Err(Error::IncorrectPassword)
        ));
        let LoginStep::Complete(account) = flow.submit_password(&client, "password").await.unwrap()
        else {
            panic!("login did not complete");
        };
        assert_eq!(account.user_id, 77);
        assert_eq!(account.secrets.token, token);
        assert_eq!(
            account.secrets.master_key,
            b64::decode(&generated.private_key_attributes.key).unwrap()
        );
        assert!(account.recovery_key.is_none());
        assert!(matches!(
            flow.submit_password(&client, "password").await,
            Err(Error::InvalidInput(_))
        ));
    }

    #[tokio::test]
    async fn passkey_login_waits_for_verification_before_requesting_password() {
        let generated =
            auth::generate_keys_with_strength("password", KeyDerivationStrength::Interactive)
                .unwrap();
        let attributes = generated.key_attributes;
        let mut server = Server::new_async().await;
        let (client, mut flow) = start_email_login(
            &mut server,
            &attributes,
            json!({"id": 77, "passkeySessionID": "passkey-1", "accountsUrl": "https://accounts.ente.io"}),
        )
        .await;
        assert!(matches!(
            flow.submit_code(&client, "123456").await.unwrap(),
            LoginStep::SecondFactor {
                totp: false,
                passkey: true
            }
        ));
        assert_eq!(
            flow.passkey_url(&client, "ente-cli://passkey").unwrap(),
            "https://accounts.ente.io/passkeys/verify?clientPackage=io.ente.photos&passkeySessionID=passkey-1&redirect=ente-cli%3A%2F%2Fpasskey"
        );
        let pending = server
            .mock("GET", "/users/two-factor/passkeys/get-token")
            .match_query(Matcher::UrlEncoded("sessionID".into(), "passkey-1".into()))
            .with_status(400)
            .create_async()
            .await;
        assert!(flow.poll_passkey(&client).await.unwrap().is_none());
        pending.assert_async().await;
        pending.remove_async().await;

        let verified = server
            .mock("GET", "/users/two-factor/passkeys/get-token")
            .match_query(Matcher::UrlEncoded("sessionID".into(), "passkey-1".into()))
            .with_body(
                json!({"id": 77, "keyAttributes": attributes, "token": b64::encode(&[255; 32])})
                    .to_string(),
            )
            .create_async()
            .await;
        assert!(matches!(
            flow.poll_passkey(&client).await.unwrap(),
            Some(LoginStep::Password)
        ));
        let LoginStep::Complete(account) = flow.submit_password(&client, "password").await.unwrap()
        else {
            panic!("login did not complete");
        };
        assert_eq!(account.secrets.token, [255; 32]);
        assert_eq!(
            account.recovery_key.as_deref(),
            Some(&*generated.private_key_attributes.recovery_key)
        );
        verified.assert_async().await;
    }
}
