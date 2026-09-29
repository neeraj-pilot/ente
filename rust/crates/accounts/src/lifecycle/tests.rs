use super::*;
use mockito::{Matcher, Server};
use serde::{Deserialize, de::DeserializeOwned};
use sha2::{Digest, Sha256};
use srp::ServerG4096;
use std::sync::{Arc, Mutex};

use crate::{
    login::{LoginFlow, LoginStep},
    signup::Signup,
    types::AccountsClientConfig,
};

#[derive(Default)]
struct MockSignupState {
    uploaded_key_attributes: Option<KeyAttributes>,
    remote_srp_attributes: Option<SrpAttributes>,
    pending_setup_id: Option<Uuid>,
    pending_client_proof: Option<Vec<u8>>,
    pending_server_proof: Option<Vec<u8>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SetUserAttributesPayload {
    key_attributes: KeyAttributes,
}

#[derive(Debug, Deserialize)]
struct SetupSrpPayload {
    #[serde(rename = "srpUserID")]
    srp_user_id: String,
    #[serde(rename = "srpSalt")]
    srp_salt: String,
    #[serde(rename = "srpVerifier")]
    srp_verifier: String,
    #[serde(rename = "srpA")]
    srp_a: String,
}

#[derive(Debug, Deserialize)]
struct CompleteSrpSetupPayload {
    #[serde(rename = "setupID")]
    setup_id: String,
    #[serde(rename = "srpM1")]
    srp_m1: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UpdateSrpPayload {
    setup_id: String,
    srp_m1: String,
    updated_key_attr: UpdatedKeyAttr,
    log_out_other_devices: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigurePasskeyRecoveryPayload {
    secret: String,
    user_secret_cipher: String,
    user_secret_nonce: String,
}

fn parse_request_body<T>(request: &mockito::Request) -> T
where
    T: DeserializeOwned,
{
    serde_json::from_str(&request.utf8_lossy_body().unwrap()).unwrap()
}

fn make_client(origin: String) -> AccountsClient {
    AccountsClient::new(
        AccountsClientConfig::new("io.ente.photos")
            .with_origin(origin)
            .with_user_agent("ente-accounts-test"),
    )
    .unwrap()
}

fn build_login_response(
    password: &str,
    token: &str,
) -> (KeyAttributes, String, String, String, String) {
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = key_gen.key_attributes.clone();
    let encrypted_token = {
        let public_key = b64::decode(&key_attributes.public_key).unwrap();
        let sealed = crypto::sealed::seal(
            token.as_bytes(),
            &crypto::PublicKey::try_from_slice(&public_key).unwrap(),
        )
        .unwrap();
        b64::encode(&sealed)
    };

    (
        key_attributes,
        encrypted_token,
        key_gen.private_key_attributes.recovery_key.into_string(),
        key_gen.private_key_attributes.key.into_string(),
        key_gen.private_key_attributes.secret_key.into_string(),
    )
}

#[tokio::test]
async fn login_retries_email_and_totp() {
    let password = "hunter2";
    let (key_attributes, encrypted_token, recovery_key, _, _) =
        build_login_response(password, "plain-auth-token");

    let mut server = Server::new_async().await;

    let srp_attrs = server
        .mock("GET", Matcher::Any)
        .match_request(|request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query() == "/users/srp/attributes?email=user%40example.org"
        })
        .with_status(200)
        .with_body(
            serde_json::json!({
                "attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1u8; 16]),
                    "memLimit": key_attributes.mem_limit,
                    "opsLimit": key_attributes.ops_limit,
                    "kekSalt": key_attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }
            })
            .to_string(),
        )
        .create_async()
        .await;

    let ott = server
        .mock("POST", "/users/ott")
        .with_status(200)
        .expect(2)
        .create_async()
        .await;

    let expired_email = server
        .mock("POST", "/users/verify-email")
        .match_body(Matcher::PartialJson(serde_json::json!({"ott": "expired"})))
        .with_status(410)
        .create_async()
        .await;
    let incorrect_email = server
        .mock("POST", "/users/verify-email")
        .match_body(Matcher::PartialJson(serde_json::json!({"ott": "wrong"})))
        .with_status(400)
        .create_async()
        .await;
    let verify_email = server
        .mock("POST", "/users/verify-email")
        .match_body(Matcher::PartialJson(serde_json::json!({"ott": "123456"})))
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 77,
                "twoFactorSessionID": "session-1",
                "passkeySessionID": "passkey-1",
                "accountsUrl": "https://accounts.ente.io",
            })
            .to_string(),
        )
        .create_async()
        .await;

    let incorrect_totp = server
        .mock("POST", "/users/two-factor/verify")
        .match_body(Matcher::PartialJson(serde_json::json!({"code": "wrong"})))
        .with_status(400)
        .create_async()
        .await;
    let verify_totp = server
        .mock("POST", "/users/two-factor/verify")
        .match_body(Matcher::PartialJson(serde_json::json!({"code": "654321"})))
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 77,
                "keyAttributes": key_attributes,
                "encryptedToken": encrypted_token,
            })
            .to_string(),
        )
        .create_async()
        .await;

    let client = make_client(server.url());

    let (mut flow, step) = LoginFlow::start(&client, "user@example.org".into())
        .await
        .unwrap();
    assert!(matches!(step, LoginStep::EmailCode));
    assert!(matches!(
        flow.submit_code(&client, "expired").await,
        Err(Error::EmailVerificationCodeExpired)
    ));
    flow.resend_code(&client).await.unwrap();
    assert!(matches!(
        flow.submit_code(&client, "wrong").await,
        Err(Error::IncorrectEmailVerificationCode)
    ));
    assert!(matches!(
        flow.submit_code(&client, "123456").await.unwrap(),
        LoginStep::SecondFactor {
            totp: true,
            passkey: true
        }
    ));
    assert!(matches!(
        flow.submit_code(&client, "wrong").await,
        Err(Error::IncorrectTotp)
    ));
    assert!(matches!(
        flow.submit_code(&client, "654321").await.unwrap(),
        LoginStep::Password
    ));
    let LoginStep::Complete(result) = flow.submit_password(&client, password).await.unwrap() else {
        panic!("login did not complete");
    };

    assert_eq!(result.user_id, 77);
    assert_eq!(result.secrets.token, b"plain-auth-token");
    assert_eq!(result.recovery_key.as_deref(), Some(recovery_key.as_str()));

    srp_attrs.assert_async().await;
    ott.assert_async().await;
    expired_email.assert_async().await;
    incorrect_email.assert_async().await;
    incorrect_totp.assert_async().await;
    verify_email.assert_async().await;
    verify_totp.assert_async().await;
}

#[tokio::test]
async fn login_with_email_mfa_treats_429_as_terminal_error() {
    let password = "hunter2";
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = key_gen.key_attributes.clone();

    let mut server = Server::new_async().await;

    let srp_attrs = server
        .mock("GET", Matcher::Any)
        .match_request(|request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query() == "/users/srp/attributes?email=user%40example.org"
        })
        .with_status(200)
        .with_body(
            serde_json::json!({
                "attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1u8; 16]),
                    "memLimit": key_attributes.mem_limit,
                    "opsLimit": key_attributes.ops_limit,
                    "kekSalt": key_attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }
            })
            .to_string(),
        )
        .create_async()
        .await;

    let ott = server
        .mock("POST", "/users/ott")
        .with_status(200)
        .create_async()
        .await;

    let verify_email = server
        .mock("POST", "/users/verify-email")
        .with_status(429)
        .with_body("too many attempts")
        .create_async()
        .await;

    let client = make_client(server.url());

    let (mut flow, _) = LoginFlow::start(&client, "user@example.org".into())
        .await
        .unwrap();
    let error = flow.submit_code(&client, "123456").await.err().unwrap();

    match error {
        Error::EmailVerificationRateLimited => {}
        other => panic!("unexpected error: {other:?}"),
    }

    srp_attrs.assert_async().await;
    ott.assert_async().await;
    verify_email.assert_async().await;
}

#[tokio::test]
async fn login_with_totp_treats_404_as_expired_session() {
    let password = "hunter2";
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = key_gen.key_attributes.clone();

    let mut server = Server::new_async().await;

    let srp_attrs = server
        .mock("GET", Matcher::Any)
        .match_request(|request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query() == "/users/srp/attributes?email=user%40example.org"
        })
        .with_status(200)
        .with_body(
            serde_json::json!({
                "attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1u8; 16]),
                    "memLimit": key_attributes.mem_limit,
                    "opsLimit": key_attributes.ops_limit,
                    "kekSalt": key_attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }
            })
            .to_string(),
        )
        .create_async()
        .await;

    let ott = server
        .mock("POST", "/users/ott")
        .with_status(200)
        .create_async()
        .await;

    let verify_email = server
        .mock("POST", "/users/verify-email")
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 77,
                "twoFactorSessionID": "session-1",
            })
            .to_string(),
        )
        .create_async()
        .await;

    let verify_totp = server
        .mock("POST", "/users/two-factor/verify")
        .with_status(404)
        .with_body("missing session")
        .create_async()
        .await;

    let client = make_client(server.url());

    let (mut flow, _) = LoginFlow::start(&client, "user@example.org".into())
        .await
        .unwrap();
    assert!(matches!(
        flow.submit_code(&client, "123456").await.unwrap(),
        LoginStep::SecondFactor { totp: true, .. }
    ));
    let error = flow.submit_code(&client, "654321").await.err().unwrap();

    match error {
        Error::SecondFactorSessionExpired => {}
        other => panic!("unexpected error: {other:?}"),
    }

    srp_attrs.assert_async().await;
    ott.assert_async().await;
    verify_email.assert_async().await;
    verify_totp.assert_async().await;
}

#[tokio::test]
async fn login_with_totp_treats_429_as_terminal_error() {
    let password = "hunter2";
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = key_gen.key_attributes.clone();

    let mut server = Server::new_async().await;

    let srp_attrs = server
        .mock("GET", Matcher::Any)
        .match_request(|request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query() == "/users/srp/attributes?email=user%40example.org"
        })
        .with_status(200)
        .with_body(
            serde_json::json!({
                "attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1u8; 16]),
                    "memLimit": key_attributes.mem_limit,
                    "opsLimit": key_attributes.ops_limit,
                    "kekSalt": key_attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }
            })
            .to_string(),
        )
        .create_async()
        .await;

    let ott = server
        .mock("POST", "/users/ott")
        .with_status(200)
        .create_async()
        .await;

    let verify_email = server
        .mock("POST", "/users/verify-email")
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 77,
                "twoFactorSessionID": "session-1",
            })
            .to_string(),
        )
        .create_async()
        .await;

    let verify_totp = server
        .mock("POST", "/users/two-factor/verify")
        .with_status(429)
        .with_body("too many attempts")
        .create_async()
        .await;

    let client = make_client(server.url());

    let (mut flow, _) = LoginFlow::start(&client, "user@example.org".into())
        .await
        .unwrap();
    assert!(matches!(
        flow.submit_code(&client, "123456").await.unwrap(),
        LoginStep::SecondFactor { totp: true, .. }
    ));
    let error = flow.submit_code(&client, "654321").await.err().unwrap();

    match error {
        Error::TotpRateLimited => {}
        other => panic!("unexpected error: {other:?}"),
    }

    srp_attrs.assert_async().await;
    ott.assert_async().await;
    verify_email.assert_async().await;
    verify_totp.assert_async().await;
}

#[tokio::test]
async fn setup_two_factor_encrypts_secret_with_recovery_key() {
    let password = "pw";
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let recovery_key = key_gen.private_key_attributes.recovery_key.into_string();
    let master_key = b64::decode(&key_gen.private_key_attributes.key).unwrap();
    let key_attributes = key_gen.key_attributes.clone();

    let mut server = Server::new_async().await;

    let setup = server
        .mock("POST", "/users/two-factor/setup")
        .match_header("x-auth-token", "session-token")
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body(
            serde_json::json!({
                "secretCode": "JBSWY3DPEHPK3PXP",
                "qrCode": "qr-png-b64"
            })
            .to_string(),
        )
        .create_async()
        .await;

    let enable = server
        .mock("POST", "/users/two-factor/enable")
        .match_header("x-auth-token", "session-token")
        .match_header("x-client-package", "io.ente.photos")
        .match_body(Matcher::Regex("\"encryptedTwoFactorSecret\"".into()))
        .with_status(200)
        .create_async()
        .await;

    let client = make_client(server.url());
    client.set_auth_token(Some("session-token".into()));

    let result = TwoFactorSetup::start(&client, &master_key, &key_attributes)
        .await
        .unwrap();
    result.enable(&client, "123123").await.unwrap();

    assert_eq!(result.secret_code, "JBSWY3DPEHPK3PXP");
    assert_eq!(result.recovery_key, recovery_key);

    setup.assert_async().await;
    enable.assert_async().await;
}

#[tokio::test]
async fn configure_passkey_recovery_accepts_hex_recovery_key() {
    let key_gen =
        auth::generate_keys_with_strength("pw", auth::KeyDerivationStrength::Interactive).unwrap();
    let recovery_key_hex = key_gen.private_key_attributes.recovery_key.into_string();
    let expected_recovery_key = hex::decode(&recovery_key_hex).unwrap();

    let mut server = Server::new_async().await;
    let configure = server
        .mock("POST", "/users/two-factor/passkeys/configure-recovery")
        .match_header("x-auth-token", "session-token")
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: ConfigurePasskeyRecoveryPayload = parse_request_body(request);
            let cipher = b64::decode(&payload.user_secret_cipher).unwrap();
            let nonce = b64::decode(&payload.user_secret_nonce).unwrap();
            let decrypted = secretbox::decrypt(
                &cipher,
                &crypto::Nonce::try_from_slice(&nonce).unwrap(),
                &crypto::Key::try_from_slice(&expected_recovery_key).unwrap(),
            )
            .unwrap();
            assert_eq!(payload.secret, "reset-secret");
            assert_eq!(String::from_utf8(decrypted).unwrap(), "reset-secret");
            Vec::new()
        })
        .create_async()
        .await;

    let client = make_client(server.url());
    client.set_auth_token(Some("session-token".into()));

    configure_passkey_recovery(&client, "reset-secret", &recovery_key_hex)
        .await
        .unwrap();

    configure.assert_async().await;
}

#[tokio::test]
async fn login_with_passkey_treats_404_as_expired_session() {
    let password = "hunter2";
    let key_gen =
        auth::generate_keys_with_strength(password, auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = key_gen.key_attributes.clone();

    let mut server = Server::new_async().await;

    let srp_attrs = server
        .mock("GET", Matcher::Any)
        .match_request(|request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query() == "/users/srp/attributes?email=user%40example.org"
        })
        .with_status(200)
        .with_body(
            serde_json::json!({
                "attributes": {
                    "srpUserID": Uuid::new_v4(),
                    "srpSalt": b64::encode(&[1u8; 16]),
                    "memLimit": key_attributes.mem_limit,
                    "opsLimit": key_attributes.ops_limit,
                    "kekSalt": key_attributes.kek_salt,
                    "isEmailMFAEnabled": true
                }
            })
            .to_string(),
        )
        .create_async()
        .await;

    let ott = server
        .mock("POST", "/users/ott")
        .with_status(200)
        .create_async()
        .await;

    let verify_email = server
        .mock("POST", "/users/verify-email")
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 77,
                "passkeySessionID": "passkey-session",
                "accountsUrl": "https://accounts.example.org"
            })
            .to_string(),
        )
        .create_async()
        .await;

    let passkey_status = server
        .mock("GET", "/users/two-factor/passkeys/get-token")
        .match_query(Matcher::UrlEncoded(
            "sessionID".into(),
            "passkey-session".into(),
        ))
        .with_status(404)
        .with_body("expired")
        .create_async()
        .await;

    let client = make_client(server.url());

    let (mut flow, _) = LoginFlow::start(&client, "user@example.org".into())
        .await
        .unwrap();
    assert!(matches!(
        flow.submit_code(&client, "123456").await.unwrap(),
        LoginStep::SecondFactor { passkey: true, .. }
    ));
    let error = flow.poll_passkey(&client).await.err().unwrap();

    match error {
        Error::SecondFactorSessionExpired => {}
        other => panic!("unexpected error: {other:?}"),
    }

    srp_attrs.assert_async().await;
    ott.assert_async().await;
    verify_email.assert_async().await;
    passkey_status.assert_async().await;
}

#[tokio::test]
async fn create_account_uploads_keys_and_completes_srp_setup() {
    let email = "fresh-user@example.org";
    let encoded_email = urlencoding::encode(email).into_owned();
    let signup_token_bytes = b"signup-session-token";
    let signup_token = b64::encode_url_safe(signup_token_bytes);
    let signup_state = Arc::new(Mutex::new(MockSignupState::default()));

    let mut server = Server::new_async().await;

    let send_otp = server
        .mock("POST", "/users/ott")
        .match_body(Matcher::PartialJson(serde_json::json!({
            "email": email,
            "purpose": "signup",
        })))
        .with_status(200)
        .create_async()
        .await;

    let verify_email = server
        .mock("POST", "/users/verify-email")
        .match_body(Matcher::PartialJson(serde_json::json!({
            "email": email,
            "ott": "123456",
            "source": "testAccount",
        })))
        .with_status(200)
        .with_body(
            serde_json::json!({
                "id": 99,
                "token": signup_token,
            })
            .to_string(),
        )
        .create_async()
        .await;

    let session_validity = server
        .mock("GET", "/users/session-validity/v2")
        .match_header("x-auth-token", signup_token.as_str())
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body(
            serde_json::json!({
                "hasSetKeys": false,
            })
            .to_string(),
        )
        .expect(2)
        .create_async()
        .await;

    let state = Arc::clone(&signup_state);
    let set_attributes = server
        .mock("PUT", "/users/attributes")
        .match_header("x-auth-token", signup_token.as_str())
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: SetUserAttributesPayload = parse_request_body(request);
            let key_attributes = payload.key_attributes;
            let mem_limit = u64::from(key_attributes.mem_limit);
            let ops_limit = u64::from(key_attributes.ops_limit);

            assert_eq!(mem_limit * ops_limit, 4_294_967_296);
            assert!(
                key_attributes
                    .master_key_encrypted_with_recovery_key
                    .is_some()
            );
            assert!(
                key_attributes
                    .recovery_key_encrypted_with_master_key
                    .is_some()
            );

            state.lock().unwrap().uploaded_key_attributes = Some(key_attributes);
            Vec::new()
        })
        .create_async()
        .await;

    let state = Arc::clone(&signup_state);
    let setup_srp = server
        .mock("POST", "/users/srp/setup")
        .match_header("x-auth-token", signup_token.as_str())
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: SetupSrpPayload = parse_request_body(request);
            let srp_user_id = Uuid::parse_str(&payload.srp_user_id).unwrap();
            let srp_salt = b64::decode(&payload.srp_salt).unwrap();
            let srp_verifier = b64::decode(&payload.srp_verifier).unwrap();
            let srp_a = b64::decode(&payload.srp_a).unwrap();
            let server = ServerG4096::<Sha256>::new();
            let b_private = [0x33u8; 64];
            let srp_b = pad_left(
                &server.compute_public_ephemeral(&b_private, &srp_verifier),
                SRP_A_LEN,
            );
            let verifier = server
                .process_reply(
                    payload.srp_user_id.as_bytes(),
                    &srp_salt,
                    &b_private,
                    &srp_verifier,
                    &srp_a,
                )
                .unwrap();
            let setup_id = Uuid::new_v4();
            let srp_a = pad_left(&srp_a, SRP_A_LEN);
            let shared_secret = pad_left(verifier.key(), SRP_A_LEN);

            let mut client_proof_hasher = Sha256::new();
            client_proof_hasher.update(&srp_a);
            client_proof_hasher.update(&srp_b);
            client_proof_hasher.update(&shared_secret);
            let client_proof = client_proof_hasher.finalize().to_vec();

            let server_key = Sha256::digest(&shared_secret);
            let mut server_proof_hasher = Sha256::new();
            server_proof_hasher.update(&srp_a);
            server_proof_hasher.update(&client_proof);
            server_proof_hasher.update(server_key);
            let server_proof = server_proof_hasher.finalize().to_vec();

            let mut state = state.lock().unwrap();
            let uploaded_key_attributes = state.uploaded_key_attributes.clone().unwrap();
            state.pending_setup_id = Some(setup_id);
            state.remote_srp_attributes = Some(SrpAttributes {
                srp_user_id,
                srp_salt: payload.srp_salt,
                mem_limit: uploaded_key_attributes.mem_limit,
                ops_limit: uploaded_key_attributes.ops_limit,
                kek_salt: uploaded_key_attributes.kek_salt,
                is_email_mfa_enabled: false,
            });
            state.pending_client_proof = Some(client_proof);
            state.pending_server_proof = Some(server_proof);

            serde_json::json!({
                "setupID": setup_id,
                "srpB": b64::encode(&srp_b),
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let state = Arc::clone(&signup_state);
    let complete_srp = server
        .mock("POST", "/users/srp/complete")
        .match_header("x-auth-token", signup_token.as_str())
        .match_header("x-client-package", "io.ente.photos")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: CompleteSrpSetupPayload = parse_request_body(request);
            let setup_id = Uuid::parse_str(&payload.setup_id).unwrap();
            let srp_m1 = b64::decode(&payload.srp_m1).unwrap();

            let mut state = state.lock().unwrap();
            assert_eq!(state.pending_setup_id, Some(setup_id));
            assert_eq!(state.pending_client_proof.take().unwrap(), srp_m1);

            serde_json::json!({
                "setupID": setup_id,
                "srpM2": b64::encode(&state.pending_server_proof.take().unwrap()),
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let state = Arc::clone(&signup_state);
    let get_srp_attributes = server
        .mock("GET", Matcher::Any)
        .match_request(move |request| {
            request.path() == "/users/srp/attributes"
                && request.path_and_query()
                    == format!("/users/srp/attributes?email={encoded_email}")
        })
        .with_status(200)
        .with_body_from_request(move |_| {
            let state = state.lock().unwrap();
            serde_json::json!({
                "attributes": state.remote_srp_attributes.as_ref().unwrap()
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let client = make_client(server.url());

    client.send_otp(email, "signup").await.unwrap();
    let verification = client
        .verify_email(email, "123456", Some("testAccount"))
        .await
        .unwrap();
    let created = Signup::verified(email.into(), verification)
        .unwrap()
        .prepare(&client, "CorrectHorseBatteryStaple!")
        .await
        .unwrap()
        .finish(&client)
        .await
        .unwrap();

    assert_eq!(created.user_id, 99);
    assert_eq!(created.secrets.token, signup_token_bytes);
    assert!(created.recovery_key.is_some());

    send_otp.assert_async().await;
    verify_email.assert_async().await;
    session_validity.assert_async().await;
    set_attributes.assert_async().await;
    setup_srp.assert_async().await;
    complete_srp.assert_async().await;
    get_srp_attributes.assert_async().await;
}

#[tokio::test]
async fn change_password_updates_srp_and_keys() {
    let original =
        auth::generate_keys_with_strength("old-password", auth::KeyDerivationStrength::Interactive)
            .unwrap();
    let key_attributes = original.key_attributes.clone();
    let master_key = b64::decode(&original.private_key_attributes.key).unwrap();
    let state = Arc::new(Mutex::new(MockSignupState::default()));

    let mut server = Server::new_async().await;

    let state_for_setup = Arc::clone(&state);
    let setup_srp = server
        .mock("POST", "/users/srp/setup")
        .match_header("x-auth-token", "session-token")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: SetupSrpPayload = parse_request_body(request);
            let srp_salt = b64::decode(&payload.srp_salt).unwrap();
            let srp_verifier = b64::decode(&payload.srp_verifier).unwrap();
            let srp_a = b64::decode(&payload.srp_a).unwrap();
            let server = ServerG4096::<Sha256>::new();
            let b_private = [0x44u8; 64];
            let srp_b = pad_left(
                &server.compute_public_ephemeral(&b_private, &srp_verifier),
                SRP_A_LEN,
            );
            let setup_id = Uuid::new_v4();
            let verifier = server
                .process_reply(
                    payload.srp_user_id.as_bytes(),
                    &srp_salt,
                    &b_private,
                    &srp_verifier,
                    &srp_a,
                )
                .unwrap();
            let srp_a = pad_left(&srp_a, SRP_A_LEN);
            let shared_secret = pad_left(verifier.key(), SRP_A_LEN);

            let mut client_proof_hasher = Sha256::new();
            client_proof_hasher.update(&srp_a);
            client_proof_hasher.update(&srp_b);
            client_proof_hasher.update(&shared_secret);
            let client_proof = client_proof_hasher.finalize().to_vec();

            let server_key = Sha256::digest(&shared_secret);
            let mut server_proof_hasher = Sha256::new();
            server_proof_hasher.update(&srp_a);
            server_proof_hasher.update(&client_proof);
            server_proof_hasher.update(server_key);
            let server_proof = server_proof_hasher.finalize().to_vec();

            let state = &mut *state_for_setup.lock().unwrap();
            state.pending_setup_id = Some(setup_id);
            state.pending_client_proof = Some(client_proof);
            state.pending_server_proof = Some(server_proof);
            state.remote_srp_attributes = Some(SrpAttributes {
                srp_user_id: Uuid::parse_str(&payload.srp_user_id).unwrap(),
                srp_salt: payload.srp_salt.clone(),
                mem_limit: 0,
                ops_limit: 0,
                kek_salt: String::new(),
                is_email_mfa_enabled: false,
            });

            serde_json::json!({
                "setupID": setup_id,
                "srpB": b64::encode(&srp_b),
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let state_for_update = Arc::clone(&state);
    let update_srp = server
        .mock("POST", "/users/srp/update")
        .match_header("x-auth-token", "session-token")
        .with_status(200)
        .with_body_from_request(move |request| {
            let payload: UpdateSrpPayload = parse_request_body(request);
            let state = &mut *state_for_update.lock().unwrap();
            assert_eq!(
                payload.setup_id,
                state.pending_setup_id.unwrap().to_string()
            );
            assert_eq!(
                b64::decode(&payload.srp_m1).unwrap(),
                state.pending_client_proof.as_ref().unwrap().clone()
            );
            assert!(payload.log_out_other_devices);
            state.remote_srp_attributes = state.remote_srp_attributes.clone().map(|mut attrs| {
                attrs.mem_limit = payload.updated_key_attr.mem_limit;
                attrs.ops_limit = payload.updated_key_attr.ops_limit;
                attrs.kek_salt = payload.updated_key_attr.kek_salt.clone();
                attrs
            });
            serde_json::json!({
                "setupID": payload.setup_id,
                "srpM2": b64::encode(state.pending_server_proof.as_ref().unwrap()),
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let state_for_attrs = Arc::clone(&state);
    let get_srp_attributes = server
        .mock("GET", "/users/srp/attributes")
        .match_query(Matcher::UrlEncoded(
            "email".into(),
            "user@example.org".into(),
        ))
        .with_status(200)
        .with_body_from_request(move |_| {
            let state = state_for_attrs.lock().unwrap();
            serde_json::json!({
                "attributes": state.remote_srp_attributes
            })
            .to_string()
            .into_bytes()
        })
        .create_async()
        .await;

    let client = make_client(server.url());
    client.set_auth_token(Some("session-token".into()));

    let result = change_password_with_strength(
        &client,
        ChangePasswordParams {
            email: "user@example.org".into(),
            password: Zeroizing::new("new-password".into()),
            master_key: SecretVec::new(master_key),
            key_attributes,
            log_out_other_devices: true,
        },
        KeyDerivationStrength::Interactive,
    )
    .await
    .unwrap();

    assert_eq!(
        result.srp_attributes.kek_salt,
        result.key_attributes.kek_salt
    );

    setup_srp.assert_async().await;
    update_srp.assert_async().await;
    get_srp_attributes.assert_async().await;
}
