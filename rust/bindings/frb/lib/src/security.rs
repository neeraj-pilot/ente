use crate::{
    accounts::{AccountsError, types::KeyAttributes},
    session::Session,
};
use ente_accounts::security;

pub async fn set_email_mfa(session: &Session, enabled: bool) -> Result<(), AccountsError> {
    security::set_email_mfa(session.as_ref(), enabled)
        .await
        .map_err(Into::into)
}

pub async fn setup_srp(
    session: &Session,
    password: String,
    key_attributes: KeyAttributes,
) -> Result<(), AccountsError> {
    security::setup_srp(session.as_ref(), &password, &key_attributes)
        .await
        .map_err(Into::into)
}
