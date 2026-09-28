use super::*;
use crate::AccountsClientConfig;
use mockito::Server;
use serde_json::json;

fn verified() -> Signup {
    Signup::verified(
        "new@example.org".into(),
        serde_json::from_value(json!({"id": 7, "token": b64::encode_url_safe(b"session-token")}))
            .unwrap(),
    )
    .unwrap()
}

fn restore(signup: &Signup) -> Signup {
    let bytes = serde_json::to_vec(signup).unwrap();
    assert!(!String::from_utf8_lossy(&bytes).contains("signup-password"));
    serde_json::from_slice(&bytes).unwrap()
}

#[tokio::test]
async fn checkpoints_preserve_keys_and_reconcile_an_accepted_upload() {
    let mut server = Server::new_async().await;
    let client =
        AccountsClient::new(AccountsClientConfig::new("io.ente.locker").with_origin(server.url()))
            .unwrap();
    let empty = server
        .mock("GET", "/users/session-validity/v2")
        .match_header(
            "x-auth-token",
            b64::encode_url_safe(b"session-token").as_str(),
        )
        .with_body(json!({"hasSetKeys": false}).to_string())
        .create_async()
        .await;
    let verified = restore(&verified());
    assert!(verified.recovery_key().unwrap().is_none());
    assert!(matches!(
        verified.finish(&client).await,
        Err(Error::InvalidInput(_))
    ));
    let prepared = verified
        .prepare_with_strength(
            &client,
            "signup-password",
            KeyDerivationStrength::Interactive,
        )
        .await
        .unwrap();
    let recovered = restore(&prepared);
    assert_eq!(
        prepared.recovery_key().unwrap(),
        recovered.recovery_key().unwrap()
    );
    let retried = recovered
        .prepare(&client, "different-password")
        .await
        .unwrap();
    assert!(serde_json::to_vec(&retried).unwrap() == serde_json::to_vec(&recovered).unwrap());
    let keys = recovered.keys.as_ref().unwrap();
    empty.assert_async().await;
    empty.remove_async().await;
    let accepted = server
        .mock("GET", "/users/session-validity/v2")
        .with_body(json!({"hasSetKeys": true, "keyAttributes": keys.attributes}).to_string())
        .create_async()
        .await;
    let setup = server
        .mock("POST", "/users/srp/setup")
        .with_status(400)
        .create_async()
        .await;
    let remote = server
        .mock("GET", "/users/srp/attributes?email=new%40example.org")
        .with_body(
            json!({"attributes": {
                "srpUserID": keys.srp_user_id, "srpSalt": b64::encode(&keys.srp_salt),
                "kekSalt": keys.attributes.kek_salt, "memLimit": keys.attributes.mem_limit,
                "opsLimit": keys.attributes.ops_limit,
            }})
            .to_string(),
        )
        .create_async()
        .await;
    let account = restore(&recovered).finish(&client).await.unwrap();
    assert_eq!(account.secrets.master_key, keys.master_key);
    assert_eq!(account.secrets.secret_key, keys.secret_key);
    assert_eq!(account.recovery_key, recovered.recovery_key().unwrap());
    accepted.assert_async().await;
    accepted.remove_async().await;
    let mut different = keys.attributes.clone();
    different.public_key = b64::encode(&[9; 32]);
    let conflict = server
        .mock("GET", "/users/session-validity/v2")
        .with_body(json!({"hasSetKeys": true, "keyAttributes": different}).to_string())
        .expect(2)
        .create_async()
        .await;
    assert!(matches!(
        recovered.finish(&client).await,
        Err(Error::AccountAlreadyExists)
    ));
    assert!(matches!(
        verified.prepare(&client, "signup-password").await,
        Err(Error::AccountAlreadyExists)
    ));
    setup.assert_async().await;
    remote.assert_async().await;
    conflict.assert_async().await;
}

#[cfg(feature = "museum")]
#[test]
fn checkpoints_resume_signup_against_museum() -> ente_test_support::TestResult {
    use ente_test_support::{HARDCODED_OTT, HARDCODED_OTT_EMAIL_SUFFIX, Museum};

    Museum::run_async(|endpoint| async move {
        let client =
            AccountsClient::new(AccountsClientConfig::new("io.ente.photos").with_origin(endpoint))?;
        let email = format!(
            "signup-resume-{}{HARDCODED_OTT_EMAIL_SUFFIX}",
            Uuid::new_v4()
        );
        client.send_otp(&email, "signup").await?;
        let response = client
            .verify_email(&email, HARDCODED_OTT, Some("testAccount"))
            .await?;
        let verified = Signup::verified(email.clone(), response)?;
        let prepared = restore(
            &restore(&verified)
                .prepare(&client, "signup-password")
                .await?,
        );
        let keys = prepared.keys.as_ref().unwrap();
        client
            .set_user_key_attributes(keys.attributes.clone())
            .await?;
        let account = restore(&prepared).finish(&client).await?;
        let resumed = restore(&prepared).finish(&client).await?;
        assert_eq!(account.secrets.master_key, keys.master_key);
        assert_eq!(resumed.secrets.master_key, account.secrets.master_key);
        assert_eq!(resumed.recovery_key, account.recovery_key);
        assert!(matches!(
            verified.prepare(&client, "different-password").await,
            Err(Error::AccountAlreadyExists)
        ));
        let attributes = client.get_srp_attributes(&email).await?;
        let (response, _) = client
            .login_with_srp("signup-password", &attributes)
            .await?;
        assert_eq!(response.id, resumed.user_id);
        Ok(())
    })
}
