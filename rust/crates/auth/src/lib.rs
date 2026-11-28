pub mod backup;
pub mod code;
#[cfg(feature = "import")]
pub mod import;
pub mod record;

pub use ente_core::user_entity::WrappedKey;
use ente_core::user_entity::{self, Endpoint, Page};
use ente_core::{Session, b64, crypto, http, otp};

const ENDPOINT: Endpoint = Endpoint::Authenticator;
const PAGE_SIZE: u16 = 5000;

#[derive(thiserror::Error, Debug)]
pub enum Error {
    #[error(transparent)]
    Http(#[from] http::Error),
    #[error(transparent)]
    Crypto(#[from] crypto::Error),
    #[error(transparent)]
    Otp(#[from] otp::Error),
    #[error("invalid Auth base64 encoding")]
    Base64(#[from] b64::DecodeError),
    #[error("unreadable Auth record")]
    InvalidRecord,
    #[error("invalid Auth code parameters")]
    InvalidCodeParameters,
    #[error("HOTP code generation is not supported yet")]
    UnsupportedHotp,
    #[error("invalid encrypted Auth export")]
    InvalidBackup,
    #[error("unsupported Auth export version")]
    UnsupportedVersion,
    #[error("invalid Auth diff: {0}")]
    InvalidDiff(&'static str),
}

pub type Result<T> = std::result::Result<T, Error>;

impl From<user_entity::Error> for Error {
    fn from(error: user_entity::Error) -> Self {
        match error {
            user_entity::Error::Http(error) => Self::Http(error),
            user_entity::Error::Crypto(error) => Self::Crypto(error),
            user_entity::Error::Base64(error) => Self::Base64(error),
            user_entity::Error::InvalidDiff(reason) => Self::InvalidDiff(reason),
        }
    }
}

pub async fn fetch_key(session: &Session) -> Result<Option<WrappedKey>> {
    Ok(user_entity::fetch_key(session, ENDPOINT).await?)
}

pub async fn diff(session: &Session, since: i64) -> Result<Page> {
    Ok(user_entity::diff(session, ENDPOINT, since, PAGE_SIZE).await?)
}
