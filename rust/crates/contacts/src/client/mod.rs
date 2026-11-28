mod attachment;
mod contact;

pub use attachment::{
    AttachmentType, delete_attachment, delete_profile_picture, get_attachment_encrypted,
    get_profile_picture, set_attachment, set_profile_picture,
};
pub use contact::{
    ContactData, ContactRecord, create_contact, delete_contact, get_contact, get_diff,
    update_contact,
};

use ente_core::Session;
use ente_core::b64;
use ente_core::crypto::{Key, SecretVec, secretbox};
use ente_core::user_entity::{self, Endpoint, WrappedKey};

use crate::Result;

const CONTACT_TYPE: &str = "contact";
const ENDPOINT: Endpoint = Endpoint::UserEntity(CONTACT_TYPE);

pub type WrappedRootContactKey = WrappedKey;

pub struct ContactOutput<T> {
    pub value: T,
    pub wrapped_root_contact_key: Option<WrappedRootContactKey>,
}

struct RootContactKey {
    key: Key,
    wrapped: WrappedRootContactKey,
}

impl RootContactKey {
    fn from_wrapped(wrapped: WrappedRootContactKey, master_key: &Key) -> Result<Self> {
        let key = wrapped.open(master_key)?;
        Ok(Self { key, wrapped })
    }

    fn encrypt_contact_key(&self, contact_key: &[u8]) -> Result<String> {
        let encrypted = secretbox::encrypt_combined(contact_key, &self.key);
        Ok(b64::encode(&encrypted))
    }

    fn decrypt_contact_key(&self, encrypted_key: &str) -> Result<SecretVec> {
        let encrypted_key = b64::decode(encrypted_key)?;
        let contact_key = secretbox::decrypt_combined(&encrypted_key, &self.key)?;
        Ok(SecretVec::new(contact_key))
    }
}

impl<T> ContactOutput<T> {
    fn new(value: T, root_contact_key: Option<RootContactKey>) -> Self {
        Self {
            value,
            wrapped_root_contact_key: root_contact_key.map(|key| key.wrapped),
        }
    }
}

async fn root_contact_key(
    session: &Session,
    cached: Option<&WrappedRootContactKey>,
) -> Result<RootContactKey> {
    let wrapped = match cached {
        Some(cached) => cached.clone(),
        None => match user_entity::fetch_key(session, ENDPOINT).await? {
            Some(wrapped) => wrapped,
            None => user_entity::ensure_key(session, CONTACT_TYPE).await?,
        },
    };
    RootContactKey::from_wrapped(wrapped, &session.master_key)
}
