use ente_core::user_entity::KeyError;
use ente_core::{b64, crypto, http};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum Error {
    #[error(transparent)]
    Http(#[from] http::Error),

    #[error(transparent)]
    Crypto(#[from] crypto::Error),

    #[error("base64 decode error: {0}")]
    Base64Decode(#[from] b64::DecodeError),

    #[error("invalid input: {0}")]
    InvalidInput(String),

    #[error("missing encrypted data for live contact")]
    MissingEncryptedData,

    #[error("missing encrypted key for live contact")]
    MissingEncryptedKey,

    #[error("profile picture not found")]
    ProfilePictureNotFound,
}

pub type Result<T> = std::result::Result<T, Error>;

impl From<KeyError> for Error {
    fn from(error: KeyError) -> Self {
        match error {
            KeyError::Crypto(error) => Self::Crypto(error),
            KeyError::Base64(error) => Self::Base64Decode(error),
        }
    }
}
