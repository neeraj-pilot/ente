mod api;
mod key_gen;
mod payload;
mod recovery;
mod srp;
mod types;

pub use srp::SrpSession;

pub use api::{DecryptedSecrets, GeneratedKek, GeneratedSrpSetup, SrpCredentials};
pub use api::{
    decrypt_keys_only, decrypt_secrets, derive_kek, derive_srp_credentials, derive_srp_login_key,
    generate_interactive_kek, generate_sensitive_kek, generate_srp_setup,
    generate_srp_setup_with_login_key,
};

pub use key_gen::{
    KeyDerivationStrength, create_new_recovery_key, generate_key_attributes_for_new_password,
    generate_key_attributes_for_new_password_with_strength, generate_keys_with_strength,
};

pub use recovery::{get_recovery_key, recovery_key_from_mnemonic_or_hex, recovery_key_to_mnemonic};

pub use types::{KeyAttributes, KeyGenResult, PrivateKeyAttributes, SrpAttributes};

pub use payload::{parse_delete_account_challenge, two_factor_recovery_proof, validate_auth_token};
