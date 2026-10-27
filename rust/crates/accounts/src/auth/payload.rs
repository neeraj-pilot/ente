use ente_core::b64;
use hmac::{Hmac, KeyInit, Mac};
use sha2::Sha256;
use zeroize::Zeroizing;

use crate::{Error, Result, models::TwoFactorType};

pub fn validate_auth_token(token: &[u8]) -> Result<()> {
    if token.len() != b"auth-token".len() + 32 || !token.starts_with(b"auth-token") {
        return Err(Error::Protocol(
            "Invalid login token. The server may need upgrading.".into(),
        ));
    }
    Ok(())
}

pub fn parse_delete_account_challenge(plaintext: &[u8]) -> Result<String> {
    let challenge = plaintext
        .strip_prefix(b"delete-jwt")
        .filter(|challenge| !challenge.is_empty())
        .ok_or_else(|| {
            Error::Protocol("Invalid deletion challenge. The server may need upgrading.".into())
        })?;
    String::from_utf8(challenge.to_vec())
        .map_err(|_| Error::Protocol("Invalid deletion challenge encoding".into()))
}

pub fn two_factor_recovery_proof(
    secret: &[u8],
    two_factor_type: TwoFactorType,
    session_id: &str,
) -> Result<String> {
    let secret = Zeroizing::new(b64::encode(secret).into_bytes());
    let factor = match two_factor_type {
        TwoFactorType::Totp => "totp",
        TwoFactorType::Passkey => "passkey",
    };
    let mut mac =
        Hmac::<Sha256>::new_from_slice(&secret).map_err(|_| Error::IncorrectRecoveryKey)?;
    mac.update(format!("ente-two-factor-recovery:v1\0{factor}\0{session_id}").as_bytes());
    Ok(b64::encode(&mac.finalize().into_bytes()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deletion_requires_its_prefix_and_valid_utf8() {
        assert_eq!(
            parse_delete_account_challenge(b"delete-jwtheader.payload.signature").unwrap(),
            "header.payload.signature",
        );
        for payload in [
            vec![7; 32],
            [b"auth-token".as_slice(), &[7; 32]].concat(),
            b"header.payload.signature".to_vec(),
            b"delete-jwt".to_vec(),
            b"delete-jwt\xff".to_vec(),
        ] {
            assert!(parse_delete_account_challenge(&payload).is_err());
        }
    }

    #[test]
    fn recovery_proofs_bind_the_session_and_factor() {
        use TwoFactorType::{Passkey, Totp};
        let totp_secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        let totp_proof = "GLPadofZrER+5+7OLkqiNxlmyS6XzaKGdsMeHg1lnvU=";
        for (factor, plaintext, expected) in [
            (Totp, b64::decode(totp_secret).unwrap(), totp_proof),
            (
                Passkey,
                (0..32).collect(),
                "PxcSH0NrKUX3u330jU6TtYp/I/mT4087vajBXIdPh0I=",
            ),
        ] {
            assert_eq!(
                two_factor_recovery_proof(&plaintext, factor, "recovery-session").unwrap(),
                expected
            );
            assert_ne!(
                two_factor_recovery_proof(&plaintext, factor, "other-session").unwrap(),
                expected
            );
            let other_factor = if factor == Totp { Passkey } else { Totp };
            assert_ne!(
                two_factor_recovery_proof(&plaintext, other_factor, "recovery-session").unwrap(),
                expected
            );
        }
    }
}
