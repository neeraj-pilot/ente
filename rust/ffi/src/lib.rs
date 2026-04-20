use base64::{Engine as _, engine::general_purpose::URL_SAFE};
use ente_accounts::{
    AccountsClient, AccountsClientConfig, AuthFlow, AuthFlowUi, AuthenticatedAccount,
    CreateAccountParams, Error as AccountError, LoginParams, OtpPurpose, Result as AccountResult,
    SecondFactorMethod, TotpPurpose,
};
use ente_core::auth::{self, KeyAttributes, KeyDerivationStrength, SrpSession};
use ente_core::crypto::{self, sealed};
use ente_wall as entegram_wall;
use ente_wall::crypto as wall_crypto;
use ente_wall::transport::{CommentResponse, PostResponse, WallKeyResponse};
use ente_wall::{AccountWallCtx, OpenAccountWallCtxInput, PrivateKeySource};
use serde::{Deserialize, Serialize};
use zeroize::Zeroizing;

uniffi::include_scaffolding!("ente_ffi");

// ---------------------------------------------------------------------------
// Error type
// ---------------------------------------------------------------------------

#[derive(Debug)]
pub enum FfiError {
    Crypto { msg: String },
    Auth { msg: String },
    Decode { msg: String },
    InvalidInput { msg: String },
}

impl std::fmt::Display for FfiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FfiError::Crypto { msg } => write!(f, "Crypto error: {msg}"),
            FfiError::Auth { msg } => write!(f, "Auth error: {msg}"),
            FfiError::Decode { msg } => write!(f, "Decode error: {msg}"),
            FfiError::InvalidInput { msg } => write!(f, "Invalid input: {msg}"),
        }
    }
}

impl std::error::Error for FfiError {}

impl From<ente_core::crypto::CryptoError> for FfiError {
    fn from(e: ente_core::crypto::CryptoError) -> Self {
        FfiError::Crypto { msg: e.to_string() }
    }
}

impl From<ente_core::auth::AuthError> for FfiError {
    fn from(e: ente_core::auth::AuthError) -> Self {
        FfiError::Auth { msg: e.to_string() }
    }
}

impl From<AccountError> for FfiError {
    fn from(e: AccountError) -> Self {
        match e {
            AccountError::InvalidInput(msg) => FfiError::InvalidInput { msg },
            other => FfiError::Auth {
                msg: other.to_string(),
            },
        }
    }
}

impl From<entegram_wall::error::WallError> for FfiError {
    fn from(e: entegram_wall::error::WallError) -> Self {
        FfiError::Crypto { msg: e.to_string() }
    }
}

// ---------------------------------------------------------------------------
// Data types (mirrors of the UDL dictionaries)
// ---------------------------------------------------------------------------

pub struct FfiKeyAttributes {
    pub kek_salt: String,
    pub encrypted_key: String,
    pub key_decryption_nonce: String,
    pub public_key: String,
    pub encrypted_secret_key: String,
    pub secret_key_decryption_nonce: String,
    pub mem_limit: Option<u32>,
    pub ops_limit: Option<u32>,
    pub master_key_encrypted_with_recovery_key: Option<String>,
    pub master_key_decryption_nonce: Option<String>,
    pub recovery_key_encrypted_with_master_key: Option<String>,
    pub recovery_key_decryption_nonce: Option<String>,
}

impl From<KeyAttributes> for FfiKeyAttributes {
    fn from(k: KeyAttributes) -> Self {
        Self {
            kek_salt: k.kek_salt,
            encrypted_key: k.encrypted_key,
            key_decryption_nonce: k.key_decryption_nonce,
            public_key: k.public_key,
            encrypted_secret_key: k.encrypted_secret_key,
            secret_key_decryption_nonce: k.secret_key_decryption_nonce,
            mem_limit: k.mem_limit,
            ops_limit: k.ops_limit,
            master_key_encrypted_with_recovery_key: k.master_key_encrypted_with_recovery_key,
            master_key_decryption_nonce: k.master_key_decryption_nonce,
            recovery_key_encrypted_with_master_key: k.recovery_key_encrypted_with_master_key,
            recovery_key_decryption_nonce: k.recovery_key_decryption_nonce,
        }
    }
}

pub struct FfiAccountLoginResult {
    pub user_id: i64,
    pub email: String,
    pub username: Option<String>,
    pub auth_token: String,
    pub master_key: Vec<u8>,
    pub secret_key: Vec<u8>,
    pub public_key: Vec<u8>,
    pub recovery_key: Option<String>,
}

pub enum FfiAccountLoginFlow {
    PasswordOnly,
    EmailOttAndPassword,
    Signup,
}

pub struct FfiAccountLoginPreflight {
    pub flow: FfiAccountLoginFlow,
}

pub struct FfiAccountSessionValidity {
    pub has_set_keys: bool,
}

pub struct FfiWallSession {
    pub base_url: String,
    pub auth_token: String,
    pub master_key: Vec<u8>,
    pub public_key: Vec<u8>,
    pub private_key: Vec<u8>,
    pub user_id: Option<i64>,
}

pub struct FfiCreatePostImage {
    pub data: Vec<u8>,
    pub position: i32,
    pub variant: String,
    pub width: i32,
    pub height: i32,
    pub blur_hash: String,
    pub content_type: Option<String>,
}

impl From<&FfiKeyAttributes> for KeyAttributes {
    fn from(k: &FfiKeyAttributes) -> Self {
        Self {
            kek_salt: k.kek_salt.clone(),
            encrypted_key: k.encrypted_key.clone(),
            key_decryption_nonce: k.key_decryption_nonce.clone(),
            public_key: k.public_key.clone(),
            encrypted_secret_key: k.encrypted_secret_key.clone(),
            secret_key_decryption_nonce: k.secret_key_decryption_nonce.clone(),
            mem_limit: k.mem_limit,
            ops_limit: k.ops_limit,
            master_key_encrypted_with_recovery_key: k
                .master_key_encrypted_with_recovery_key
                .clone(),
            master_key_decryption_nonce: k.master_key_decryption_nonce.clone(),
            recovery_key_encrypted_with_master_key: k
                .recovery_key_encrypted_with_master_key
                .clone(),
            recovery_key_decryption_nonce: k.recovery_key_decryption_nonce.clone(),
        }
    }
}

pub struct FfiKeyGenResult {
    pub key_attributes: FfiKeyAttributes,
    pub master_key_b64: String,
    pub recovery_key_hex: String,
    pub secret_key_b64: String,
    pub key_encryption_key: Vec<u8>,
    pub login_key: Vec<u8>,
}

pub struct FfiGeneratedKek {
    pub key: Vec<u8>,
    pub salt: Vec<u8>,
    pub mem_limit: u32,
    pub ops_limit: u32,
}

pub struct FfiDecryptedKeys {
    pub master_key: Vec<u8>,
    pub secret_key: Vec<u8>,
}

pub struct FfiDecryptedSecrets {
    pub master_key: Vec<u8>,
    pub secret_key: Vec<u8>,
    pub token: Vec<u8>,
}

pub struct FfiSrpSetup {
    pub srp_salt: Vec<u8>,
    pub srp_verifier: Vec<u8>,
    pub login_sub_key: Vec<u8>,
}

pub struct FfiEntityKeyPayload {
    pub encrypted_key: String,
    pub nonce: String,
}

pub struct FfiSplitCiphertext {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

pub struct FfiUnpackedPayload {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppWall {
    id: String,
    slug: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    display_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    bio: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    avatar_object_key: Option<String>,
    key_version: i32,
    #[serde(skip_serializing_if = "Option::is_none")]
    created_at: Option<String>,
    follower_count: i64,
    following_count: i64,
    post_count: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppPostAsset {
    position: i32,
    variant: String,
    object_key: String,
    blur_hash: String,
    aspect: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppPost {
    id: i64,
    wall_id: String,
    author_slug: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    author_display_name: Option<String>,
    created_at: String,
    caption: String,
    assets: Vec<AppPostAsset>,
    like_count: i64,
    comment_count: i64,
    viewer_liked: bool,
    post_key_b64: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppComment {
    id: i64,
    post_id: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    parent_id: Option<i64>,
    author_slug: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    author_display_name: Option<String>,
    text: String,
    created_at: String,
    reply_count: i32,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppPage<T> {
    items: Vec<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    next_cursor: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppFollowRequest {
    id: i64,
    from_user_id: i64,
    from_slug: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    from_display_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    from_public_key_b64: Option<String>,
    wall_id: String,
    created_at: String,
    direction: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct AppCommunityResult {
    id: String,
    slug: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    display_name: Option<String>,
    follower_count: i64,
    relationship: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProfileEnvelope {
    #[serde(default)]
    display: Option<String>,
    #[serde(default)]
    display_name: Option<String>,
    #[serde(default)]
    bio: Option<String>,
}

// ---------------------------------------------------------------------------
// SRP session wrapper (stateful object)
// ---------------------------------------------------------------------------

pub struct FfiSrpSession {
    inner: std::sync::Mutex<SrpSession>,
}

impl FfiSrpSession {
    pub fn new(
        srp_user_id: String,
        srp_salt: Vec<u8>,
        login_key: Vec<u8>,
    ) -> Result<Self, FfiError> {
        let session = SrpSession::new(&srp_user_id, &srp_salt, &login_key)?;
        Ok(Self {
            inner: std::sync::Mutex::new(session),
        })
    }

    pub fn public_a(&self) -> Vec<u8> {
        self.inner.lock().unwrap().public_a()
    }

    pub fn compute_m1(&self, server_b: Vec<u8>) -> Result<Vec<u8>, FfiError> {
        let mut session = self.inner.lock().unwrap();
        Ok(session.compute_m1(&server_b)?)
    }

    pub fn verify_m2(&self, server_m2: Vec<u8>) -> Result<bool, FfiError> {
        let session = self.inner.lock().unwrap();
        match session.verify_m2(&server_m2) {
            Ok(()) => Ok(true),
            Err(e) => Err(FfiError::Auth { msg: e.to_string() }),
        }
    }
}

// ---------------------------------------------------------------------------
// Free functions
// ---------------------------------------------------------------------------

struct FfiAuthUi {
    otp: Option<String>,
}

impl FfiAuthUi {
    fn with_otp(otp: String) -> Self {
        Self { otp: Some(otp) }
    }

    fn without_otp() -> Self {
        Self { otp: None }
    }
}

impl AuthFlowUi for FfiAuthUi {
    fn read_email_otp(
        &mut self,
        _email: &str,
        _purpose: OtpPurpose,
        _resent: bool,
    ) -> AccountResult<String> {
        self.otp.clone().ok_or_else(|| {
            AccountError::InvalidInput("email OTP is required for this account flow".into())
        })
    }

    fn read_totp_code(&mut self, _purpose: TotpPurpose) -> AccountResult<String> {
        Err(AccountError::InvalidInput(
            "TOTP is not wired through the mobile FFI account flow yet".into(),
        ))
    }

    fn report_retryable_error(&mut self, _message: &str) -> AccountResult<()> {
        Ok(())
    }

    fn choose_second_factor(
        &mut self,
        _methods: &[SecondFactorMethod],
    ) -> AccountResult<SecondFactorMethod> {
        Err(AccountError::InvalidInput(
            "second-factor selection is not wired through the mobile FFI account flow yet".into(),
        ))
    }

    fn present_passkey_verification(&mut self, _url: &str) -> AccountResult<()> {
        Err(AccountError::InvalidInput(
            "passkey verification is not wired through the mobile FFI account flow yet".into(),
        ))
    }

    fn wait_for_passkey_verification(&mut self) -> AccountResult<()> {
        Err(AccountError::InvalidInput(
            "passkey verification is not wired through the mobile FFI account flow yet".into(),
        ))
    }

    fn present_totp_secret(&mut self, _secret_code: &str, _qr_code: &str) -> AccountResult<()> {
        Err(AccountError::InvalidInput(
            "TOTP setup is not wired through the mobile FFI account flow yet".into(),
        ))
    }
}

fn block_on<F, T>(future: F) -> Result<T, FfiError>
where
    F: std::future::Future<Output = AccountResult<T>>,
{
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(1)
        .enable_all()
        .build()
        .map_err(|e| FfiError::Auth {
            msg: format!("failed to start account runtime: {e}"),
        })?;
    runtime.block_on(future).map_err(FfiError::from)
}

fn accounts_client(base_url: &str, auth_token: Option<String>) -> AccountResult<AccountsClient> {
    let mut config = AccountsClientConfig::new("io.ente.entegram")
        .with_base_url(base_url)
        .with_client_version(env!("CARGO_PKG_VERSION"));
    if let Some(token) = auth_token {
        config = config.with_auth_token(token);
    }
    AccountsClient::new(config)
}

fn account_login_result(
    _base_url: &str,
    email: String,
    account: AuthenticatedAccount,
) -> FfiAccountLoginResult {
    FfiAccountLoginResult {
        user_id: account.user_id,
        email,
        username: None,
        auth_token: URL_SAFE.encode(&account.secrets.token),
        master_key: account.secrets.master_key.clone(),
        secret_key: account.secrets.secret_key.clone(),
        public_key: account.secrets.public_key.clone(),
        recovery_key: account.recovery_key,
    }
}

fn account_send_ott(base_url: String, email: String, purpose: String) -> Result<(), FfiError> {
    block_on(async move {
        let client = accounts_client(&base_url, None)?;
        client.send_otp(&email, &purpose).await
    })
}

fn account_login(
    base_url: String,
    email: String,
    password: String,
) -> Result<FfiAccountLoginResult, FfiError> {
    crypto::init()?;
    block_on(async move {
        let client = accounts_client(&base_url, None)?;
        let mut ui = FfiAuthUi::without_otp();
        let mut flow = AuthFlow::new(&client, &mut ui);
        let account = flow
            .login(LoginParams {
                email: email.clone(),
                password: Zeroizing::new(password),
            })
            .await?;
        Ok(account_login_result(&base_url, email, account))
    })
}

fn account_login_preflight(
    base_url: String,
    email: String,
) -> Result<FfiAccountLoginPreflight, FfiError> {
    let flow = block_on(async move {
        let client = accounts_client(&base_url, None)?;
        match client.get_srp_attributes(&email).await {
            Ok(attrs) if attrs.is_email_mfa_enabled => Ok(FfiAccountLoginFlow::EmailOttAndPassword),
            Ok(_) => Ok(FfiAccountLoginFlow::PasswordOnly),
            Err(err) if err.is_http_status(&[404]) => Ok(FfiAccountLoginFlow::Signup),
            Err(err) => Err(err),
        }
    })?;
    Ok(FfiAccountLoginPreflight { flow })
}

fn account_login_with_ott(
    base_url: String,
    email: String,
    ott: String,
    password: String,
) -> Result<FfiAccountLoginResult, FfiError> {
    crypto::init()?;
    block_on(async move {
        let client = accounts_client(&base_url, None)?;
        let mut ui = FfiAuthUi::with_otp(ott);
        let mut flow = AuthFlow::new(&client, &mut ui);
        let account = flow
            .login(LoginParams {
                email: email.clone(),
                password: Zeroizing::new(password),
            })
            .await?;
        Ok(account_login_result(&base_url, email, account))
    })
}

fn account_recover(
    base_url: String,
    email: String,
    ott: String,
    recovery_key: String,
    new_password: String,
) -> Result<FfiAccountLoginResult, FfiError> {
    let _ = (base_url, email, ott, recovery_key, new_password);
    Err(FfiError::InvalidInput {
        msg: "account recovery is not exposed by ente3/rust/accounts yet".into(),
    })
}

fn account_list_device_sessions_json(
    base_url: String,
    auth_token: String,
) -> Result<String, FfiError> {
    let _ = (base_url, auth_token);
    Err(FfiError::InvalidInput {
        msg: "device session listing is not exposed by ente3/rust/accounts yet".into(),
    })
}

fn account_session_validity(
    base_url: String,
    auth_token: String,
) -> Result<FfiAccountSessionValidity, FfiError> {
    let validity = block_on(async move {
        let client = accounts_client(&base_url, Some(auth_token))?;
        client.get_session_validity().await
    })?;
    Ok(FfiAccountSessionValidity {
        has_set_keys: validity.has_set_keys,
    })
}

fn account_revoke_device_session(
    base_url: String,
    auth_token: String,
    device_id: String,
) -> Result<(), FfiError> {
    let _ = (base_url, auth_token, device_id);
    Err(FfiError::InvalidInput {
        msg: "device session revocation is not exposed by ente3/rust/accounts yet".into(),
    })
}

fn account_logout(base_url: String, auth_token: String) -> Result<(), FfiError> {
    block_on(async move {
        let client = accounts_client(&base_url, Some(auth_token))?;
        client.logout().await
    })
}

fn account_change_password(
    base_url: String,
    auth_token: String,
    master_key: Vec<u8>,
    new_password: String,
    log_out_other_devices: bool,
) -> Result<(), FfiError> {
    let _ = (
        base_url,
        auth_token,
        master_key,
        new_password,
        log_out_other_devices,
    );
    Err(FfiError::InvalidInput {
        msg: "password changes require email and key attributes in the ente3 account contract"
            .into(),
    })
}

fn block_on_wall<F, T>(future: F) -> Result<T, FfiError>
where
    F: std::future::Future<Output = Result<T, entegram_wall::error::WallError>>,
{
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(1)
        .enable_all()
        .build()
        .map_err(|e| FfiError::Crypto {
            msg: format!("failed to start wall runtime: {e}"),
        })?;
    runtime.block_on(future).map_err(FfiError::from)
}

fn open_wall_ctx(session: &FfiWallSession) -> Result<AccountWallCtx, FfiError> {
    AccountWallCtx::open(OpenAccountWallCtxInput {
        base_url: session.base_url.clone(),
        auth_token: session.auth_token.clone(),
        master_key: session.master_key.clone(),
        public_key: session.public_key.clone(),
        private_key_source: PrivateKeySource::Plain(session.private_key.clone()),
        user_id: session.user_id,
        user_agent: None,
        client_package: None,
        client_version: None,
    })
    .map_err(FfiError::from)
}

fn to_json<T: Serialize>(value: &T) -> Result<String, FfiError> {
    serde_json::to_string(value).map_err(|e| FfiError::Decode {
        msg: format!("json encode: {e}"),
    })
}

fn decode_b64_str(value: &str) -> Result<Vec<u8>, FfiError> {
    wall_crypto::decode_b64(value).map_err(|e| FfiError::Decode { msg: e.to_string() })
}

fn utf8_or_lossy(bytes: Vec<u8>) -> String {
    String::from_utf8(bytes)
        .unwrap_or_else(|err| String::from_utf8_lossy(&err.into_bytes()).into_owned())
}

fn compute_blur_hash(image_bytes: &[u8]) -> Result<String, FfiError> {
    let image = image::load_from_memory(image_bytes).map_err(|e| FfiError::Decode {
        msg: format!("decode blurhash image: {e}"),
    })?;
    let rgba = image.to_rgba8();
    let (width, height) = rgba.dimensions();
    blurhash::encode(4, 3, width, height, rgba.as_raw()).map_err(|e| FfiError::Decode {
        msg: format!("encode blurhash: {e}"),
    })
}

fn normalize_optional_string(value: Option<String>) -> Option<String> {
    value.and_then(|raw| {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            None
        } else {
            Some(trimmed.to_owned())
        }
    })
}

fn encode_profile_bytes(
    wall_slug: &str,
    display_name: Option<String>,
    bio: Option<String>,
) -> Result<Vec<u8>, entegram_wall::error::WallError> {
    let profile = serde_json::json!({
        "displayName": display_name.unwrap_or_else(|| wall_slug.to_owned()),
        "bio": bio.unwrap_or_default()
    });
    serde_json::to_vec(&profile)
        .map_err(|e| entegram_wall::error::WallError::InvalidInput(format!("profile json: {e}")))
}

fn parse_profile(profile: &[u8]) -> ProfileEnvelope {
    serde_json::from_slice::<ProfileEnvelope>(profile).unwrap_or_default()
}

fn profile_display_name(profile: &ProfileEnvelope) -> Option<String> {
    profile
        .display_name
        .clone()
        .or_else(|| profile.display.clone())
        .filter(|value| !value.trim().is_empty())
}

async fn load_wall_counts(
    ctx: &AccountWallCtx,
    wall_slug: &str,
) -> Result<(i64, i64, i64), entegram_wall::error::WallError> {
    let response = ctx.search_community(wall_slug, None, Some(10)).await?;
    let Some(record) = response
        .users
        .into_iter()
        .find(|value| value.wall_slug.eq_ignore_ascii_case(wall_slug))
    else {
        return Ok((0, 0, 0));
    };
    Ok((record.followers, record.following, record.posts))
}

async fn app_wall_from_lookup(
    ctx: &AccountWallCtx,
    wall_slug: &str,
) -> Result<Option<AppWall>, entegram_wall::error::WallError> {
    let lookup = match ctx.lookup_wall_by_slug(wall_slug).await {
        Ok(value) => value,
        Err(entegram_wall::error::WallError::Http(ente_core::http::Error::Http {
            status: 404,
            ..
        })) => {
            return Ok(None);
        }
        Err(err) => return Err(err),
    };
    let profile = match ctx.get_wall_profile_decrypted(&lookup.wall_id, None).await {
        Ok(value) => value,
        Err(entegram_wall::error::WallError::Http(ente_core::http::Error::Http {
            status: 403,
            ..
        })) => {
            let (followers, following, posts) = load_wall_counts(ctx, &lookup.wall_slug)
                .await
                .unwrap_or((0, 0, 0));
            return Ok(Some(AppWall {
                id: lookup.wall_id,
                slug: lookup.wall_slug,
                display_name: None,
                bio: None,
                avatar_object_key: None,
                key_version: 0,
                created_at: None,
                follower_count: followers,
                following_count: following,
                post_count: posts,
            }));
        }
        Err(err) => return Err(err),
    };
    let parsed = parse_profile(&profile.profile);
    let (followers, following, posts) = load_wall_counts(ctx, &lookup.wall_slug)
        .await
        .unwrap_or((0, 0, 0));
    Ok(Some(AppWall {
        id: lookup.wall_id,
        slug: lookup.wall_slug,
        display_name: profile_display_name(&parsed),
        bio: parsed.bio.filter(|value| !value.trim().is_empty()),
        avatar_object_key: profile.avatar.map(|value| value.object_key),
        key_version: profile.version,
        created_at: None,
        follower_count: followers,
        following_count: following,
        post_count: posts,
    }))
}

async fn app_wall_from_owned_record(
    ctx: &AccountWallCtx,
    record: &WallKeyResponse,
) -> Result<AppWall, entegram_wall::error::WallError> {
    let profile = ctx
        .get_wall_profile_decrypted(&record.wall_id, Some(record.key_version))
        .await?;
    let parsed = parse_profile(&profile.profile);
    let (followers, following, posts) = load_wall_counts(ctx, &record.wall_slug)
        .await
        .unwrap_or((0, 0, 0));
    Ok(AppWall {
        id: record.wall_id.clone(),
        slug: record.wall_slug.clone(),
        display_name: profile_display_name(&parsed),
        bio: parsed.bio.filter(|value| !value.trim().is_empty()),
        avatar_object_key: profile.avatar.map(|value| value.object_key),
        key_version: record.key_version,
        created_at: None,
        follower_count: followers,
        following_count: following,
        post_count: posts,
    })
}

async fn app_post_from_response(
    ctx: &AccountWallCtx,
    post: &PostResponse,
) -> Result<AppPost, entegram_wall::error::WallError> {
    let decrypted = ctx.decrypt_post_for_wall(&post.wall_id, post).await?;
    Ok(AppPost {
        id: post.post_id,
        wall_id: post.wall_id.clone(),
        author_slug: post.author.clone(),
        author_display_name: None,
        created_at: post.created_at.clone(),
        caption: decrypted
            .caption_plaintext
            .map(utf8_or_lossy)
            .unwrap_or_default(),
        assets: post
            .objects
            .iter()
            .map(|object| {
                let blur_hash = ctx
                    .decrypt_blur_hash(&decrypted.post_key, object)
                    .ok()
                    .flatten()
                    .unwrap_or_default();
                AppPostAsset {
                    position: object.position.unwrap_or_default(),
                    variant: object.variant.clone().unwrap_or_else(|| "full".to_owned()),
                    object_key: object.object_key.clone(),
                    blur_hash,
                    aspect: "square".to_owned(),
                }
            })
            .collect(),
        like_count: post.likes,
        comment_count: post.comments,
        viewer_liked: post.viewer_liked,
        post_key_b64: wall_crypto::encode_b64(&decrypted.post_key),
    })
}

async fn app_comment_from_response(
    ctx: &AccountWallCtx,
    post_key: &[u8],
    post_id: i64,
    comment: &CommentResponse,
) -> Result<AppComment, entegram_wall::error::WallError> {
    let decrypted = ctx.decrypt_comment(post_key, comment)?;
    Ok(AppComment {
        id: comment.comment_id,
        post_id,
        parent_id: comment.parent_comment_id,
        author_slug: comment.author.clone(),
        author_display_name: None,
        text: utf8_or_lossy(decrypted.plaintext),
        created_at: comment.created_at.clone(),
        reply_count: comment.replies.len() as i32,
    })
}

async fn collect_app_comments_from_response(
    ctx: &AccountWallCtx,
    post_key: &[u8],
    post_id: i64,
    comment: &CommentResponse,
    items: &mut Vec<AppComment>,
) -> Result<(), entegram_wall::error::WallError> {
    items.push(app_comment_from_response(ctx, post_key, post_id, comment).await?);
    for reply in &comment.replies {
        items.push(app_comment_from_response(ctx, post_key, post_id, reply).await?);
    }
    Ok(())
}

fn account_signup(
    base_url: String,
    email: String,
    ott: String,
    password: String,
) -> Result<FfiAccountLoginResult, FfiError> {
    crypto::init()?;
    block_on(async move {
        let client = accounts_client(&base_url, None)?;
        let otp = ott.trim().to_owned();
        let mut ui = FfiAuthUi::without_otp();
        let mut flow = AuthFlow::new(&client, &mut ui);
        let account = flow
            .create_account_with_otp(
                CreateAccountParams {
                    email: email.clone(),
                    password: Zeroizing::new(password),
                    source: Some("entegram".to_owned()),
                },
                &otp,
            )
            .await?;
        Ok(account_login_result(&base_url, email, account))
    })
}

fn wall_list_owned_walls_json(session: FfiWallSession) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let records = ctx.list_owned_walls().await?;
        let mut walls = Vec::with_capacity(records.len());
        for record in &records {
            walls.push(app_wall_from_owned_record(&ctx, record).await?);
        }
        to_json(&walls)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_lookup_by_slug_json(
    session: FfiWallSession,
    wall_slug: String,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let wall = app_wall_from_lookup(&ctx, &wall_slug).await?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_create_json(
    session: FfiWallSession,
    wall_slug: String,
    display_name: Option<String>,
    bio: Option<String>,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let profile_bytes = encode_profile_bytes(
            &wall_slug,
            normalize_optional_string(display_name),
            normalize_optional_string(bio),
        )?;
        let created = ctx.create_wall(&wall_slug, &profile_bytes).await?;
        let wall = app_wall_from_lookup(&ctx, &created.wall_slug)
            .await?
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(
                    "created wall was not readable".into(),
                )
            })?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_update_slug_json(
    session: FfiWallSession,
    wall_id: String,
    wall_slug: String,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let renamed = ctx.update_wall_slug(&wall_id, wall_slug.trim()).await?;
        let wall = app_wall_from_lookup(&ctx, &renamed.wall_slug)
            .await?
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(
                    "renamed wall was not readable".into(),
                )
            })?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_update_profile_json(
    session: FfiWallSession,
    wall_id: String,
    display_name: Option<String>,
    bio: Option<String>,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let current = ctx.get_wall_profile_decrypted(&wall_id, None).await?;
        let profile_bytes = encode_profile_bytes(
            &current.wall_slug,
            normalize_optional_string(display_name),
            normalize_optional_string(bio),
        )?;
        ctx.update_wall_profile(&wall_id, &profile_bytes, None, false)
            .await?;
        let wall = app_wall_from_lookup(&ctx, &current.wall_slug)
            .await?
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(
                    "updated wall was not readable".into(),
                )
            })?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_upload_avatar_json(
    session: FfiWallSession,
    wall_id: String,
    jpeg_data: Vec<u8>,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let current = ctx.get_wall_profile_decrypted(&wall_id, None).await?;
        let access = ctx.resolve_owned_wall_key(&wall_id).await?.ok_or_else(|| {
            entegram_wall::error::WallError::InvalidInput(format!(
                "wall {wall_id} is not owned by the account"
            ))
        })?;
        let avatar = ctx
            .upload_avatar(&wall_id, &access, &jpeg_data, "image/jpeg")
            .await?;
        ctx.update_wall_profile(&wall_id, &current.profile, Some(avatar), false)
            .await?;
        let wall = app_wall_from_lookup(&ctx, &current.wall_slug)
            .await?
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(
                    "updated wall was not readable".into(),
                )
            })?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_remove_avatar_json(session: FfiWallSession, wall_id: String) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let current = ctx.get_wall_profile_decrypted(&wall_id, None).await?;
        ctx.update_wall_profile(&wall_id, &current.profile, None, true)
            .await?;
        let wall = app_wall_from_lookup(&ctx, &current.wall_slug)
            .await?
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(
                    "updated wall was not readable".into(),
                )
            })?;
        to_json(&wall).map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_list_wall_posts_json(
    session: FfiWallSession,
    wall_id: String,
    _cursor: Option<String>,
    limit: i32,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let posts = ctx.list_posts(&wall_id, Some(limit)).await?;
        let mut items = Vec::with_capacity(posts.len());
        for post in &posts {
            items.push(app_post_from_response(&ctx, post).await?);
        }
        to_json(&AppPage {
            items,
            next_cursor: None::<String>,
        })
        .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_list_feed_json(
    session: FfiWallSession,
    cursor: Option<String>,
    limit: i32,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let page = ctx.list_feed(cursor, Some(limit)).await?;
        let mut items = Vec::with_capacity(page.items.len());
        for item in &page.items {
            let post = PostResponse {
                post_id: item.post_id,
                wall_id: item.wall_id.clone(),
                wall_slug: item.wall_slug.clone(),
                author: item.wall_slug.clone(),
                encrypted_post_key: item.encrypted_post_key.clone(),
                caption_cipher: item.caption_cipher.clone(),
                key_version: item.key_version,
                objects: item.objects.clone(),
                created_at: item.created_at.clone(),
                likes: item.likes,
                viewer_liked: item.viewer_liked,
                comments: item.comments,
            };
            items.push(app_post_from_response(&ctx, &post).await?);
        }
        let next_cursor = if page.next_cursor.trim().is_empty() {
            None
        } else {
            Some(page.next_cursor)
        };
        to_json(&AppPage { items, next_cursor })
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_fetch_post_json(session: FfiWallSession, post_id: i64) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let path = format!("/wall/posts/{post_id}");
        let post: PostResponse = ctx.client().get_json(&path, &[]).await?;
        let mapped = app_post_from_response(&ctx, &post).await?;
        to_json(&mapped)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_create_post_json(
    session: FfiWallSession,
    wall_id: String,
    caption: String,
    images: Vec<FfiCreatePostImage>,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let post_key = ctx.generate_post_key();
        let mut uploaded = Vec::with_capacity(images.len());
        for image in images.into_iter() {
            let computed_blur_hash =
                if image.variant == "thumbnail" && image.blur_hash.trim().is_empty() {
                    Some(compute_blur_hash(&image.data).map_err(|err| {
                        entegram_wall::error::WallError::InvalidInput(err.to_string())
                    })?)
                } else {
                    None
                };
            let mut asset = ctx
                .upload_post_asset(
                    &post_key,
                    &image.data,
                    image.content_type.as_deref().unwrap_or("image/jpeg"),
                    Some(image.position),
                )
                .await?;
            asset.variant = Some(image.variant);
            let blur_hash = computed_blur_hash
                .as_deref()
                .unwrap_or(image.blur_hash.as_str())
                .trim();
            if !blur_hash.is_empty() {
                asset.blur_hash_cipher = Some(wall_crypto::encode_b64(
                    &wall_crypto::encrypt_secretbox_packed(&post_key, blur_hash.as_bytes())?,
                ));
            }
            uploaded.push(asset);
        }
        let (post_id, _) = ctx
            .create_post(
                &wall_id,
                &uploaded,
                if caption.is_empty() {
                    None
                } else {
                    Some(caption.as_bytes())
                },
                Some(&post_key),
            )
            .await?;
        let path = format!("/wall/posts/{post_id}");
        let post: PostResponse = ctx.client().get_json(&path, &[]).await?;
        let mapped = app_post_from_response(&ctx, &post).await?;
        to_json(&mapped)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_set_post_like(session: FfiWallSession, post_id: i64, like: bool) -> Result<bool, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        Ok(ctx.like_post(post_id, like).await?.liked)
    })
}

fn wall_delete_post(session: FfiWallSession, post_id: i64) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.delete_post(post_id).await?;
        Ok(())
    })
}

fn wall_list_comments_json(
    session: FfiWallSession,
    post_id: i64,
    post_key_b64: Option<String>,
    cursor: Option<String>,
    limit: i32,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let post_key = match post_key_b64 {
            Some(value) => decode_b64_str(&value)
                .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?,
            None => {
                let path = format!("/wall/posts/{post_id}");
                let post: PostResponse = ctx.client().get_json(&path, &[]).await?;
                ctx.decrypt_post_for_wall(&post.wall_id, &post)
                    .await?
                    .post_key
            }
        };
        let page = ctx
            .list_comments(
                post_id,
                Some(limit),
                cursor.and_then(|value| value.parse::<i64>().ok()),
            )
            .await?;
        let nested_replies = page
            .comments
            .iter()
            .map(|comment| comment.replies.len())
            .sum::<usize>();
        let mut items = Vec::with_capacity(page.comments.len() + nested_replies);
        for comment in &page.comments {
            collect_app_comments_from_response(&ctx, &post_key, post_id, comment, &mut items)
                .await?;
        }
        let next_cursor = if page.next_cursor.trim().is_empty() {
            None
        } else {
            Some(page.next_cursor)
        };
        to_json(&AppPage { items, next_cursor })
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_create_comment_json(
    session: FfiWallSession,
    post_id: i64,
    post_key_b64: String,
    text: String,
    parent_comment_id: Option<i64>,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let post_key = decode_b64_str(&post_key_b64)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let comment = ctx
            .create_comment(post_id, &post_key, text.as_bytes(), parent_comment_id)
            .await?;
        let mapped = app_comment_from_response(&ctx, &post_key, post_id, &comment).await?;
        to_json(&mapped)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_delete_comment(
    session: FfiWallSession,
    post_id: i64,
    comment_id: i64,
) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.delete_comment(post_id, comment_id).await
    })
}

fn wall_search_community_json(
    session: FfiWallSession,
    query: String,
    cursor: Option<String>,
    limit: i32,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let response = ctx.search_community(&query, cursor, Some(limit)).await?;
        let items: Vec<AppCommunityResult> = response
            .users
            .into_iter()
            .map(|user| AppCommunityResult {
                id: user.wall_id,
                slug: user.wall_slug,
                display_name: None,
                follower_count: user.followers,
                relationship: if user.relationship.trim().is_empty() {
                    None
                } else {
                    Some(user.relationship)
                },
            })
            .collect();
        to_json(&items)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_list_follow_requests_json(
    session: FfiWallSession,
    direction: String,
) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let items = match direction.as_str() {
            "incoming" => {
                let requests = ctx.list_incoming_follow_requests().await?;
                requests
                    .into_iter()
                    .map(|request| AppFollowRequest {
                        id: request.request_id,
                        from_user_id: 0,
                        from_slug: request.follower,
                        from_display_name: None,
                        from_public_key_b64: Some(request.follower_public_key),
                        wall_id: request.wall_id,
                        created_at: request.created_at,
                        direction: "incoming".to_owned(),
                    })
                    .collect::<Vec<_>>()
            }
            "outgoing" => {
                let requests = ctx.list_outgoing_follow_requests().await?;
                requests
                    .into_iter()
                    .map(|request| AppFollowRequest {
                        id: request.request_id,
                        from_user_id: 0,
                        from_slug: request.followee,
                        from_display_name: None,
                        from_public_key_b64: None,
                        wall_id: request.wall_id,
                        created_at: request.created_at,
                        direction: "outgoing".to_owned(),
                    })
                    .collect::<Vec<_>>()
            }
            _ => {
                return Err(entegram_wall::error::WallError::InvalidInput(
                    "direction must be incoming or outgoing".into(),
                ));
            }
        };
        to_json(&items)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_list_followers_json(session: FfiWallSession, wall_id: String) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let followers = ctx.list_wall_followers(&wall_id).await?;
        let items: Vec<AppFollowRequest> = followers
            .into_iter()
            .map(|follower| AppFollowRequest {
                id: follower.follower_id,
                from_user_id: follower.follower_id,
                from_slug: follower.username,
                from_display_name: None,
                from_public_key_b64: Some(follower.public_key),
                wall_id: wall_id.clone(),
                created_at: follower.created_at,
                direction: "incoming".to_owned(),
            })
            .collect();
        to_json(&items)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_list_following_json(session: FfiWallSession) -> Result<String, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let shares = ctx.list_follow_shares().await?;
        let items: Vec<AppFollowRequest> = shares
            .into_iter()
            .map(|share| AppFollowRequest {
                id: 0,
                from_user_id: 0,
                from_slug: share.wall_slug,
                from_display_name: None,
                from_public_key_b64: None,
                wall_id: share.wall_id,
                created_at: String::new(),
                direction: "following".to_owned(),
            })
            .collect();
        to_json(&items)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))
    })
}

fn wall_request_follow(session: FfiWallSession, wall_id: String) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.request_follow_by_wall(&wall_id).await.map(|_| ())
    })
}

fn wall_approve_follow_request(session: FfiWallSession, request_id: i64) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let request = ctx
            .list_incoming_follow_requests()
            .await?
            .into_iter()
            .find(|value| value.request_id == request_id)
            .ok_or_else(|| {
                entegram_wall::error::WallError::InvalidInput(format!(
                    "follow request {request_id} not found"
                ))
            })?;
        ctx.approve_follow_request(&request).await.map(|_| ())
    })
}

fn wall_reject_follow_request(session: FfiWallSession, request_id: i64) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.reject_follow_request(request_id).await.map(|_| ())
    })
}

fn wall_cancel_follow_request(session: FfiWallSession, request_id: i64) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.cancel_follow_request(request_id).await.map(|_| ())
    })
}

fn wall_unfollow(session: FfiWallSession, wall_id: String) -> Result<(), FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.unfollow_by_wall(&wall_id).await.map(|_| ())
    })
}

fn wall_load_asset_bytes(
    session: FfiWallSession,
    wall_id: String,
    object_key: String,
    post_key_b64: String,
) -> Result<Vec<u8>, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let post_key = decode_b64_str(&post_key_b64)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        ctx.download_decrypted_asset(&wall_id, &object_key, &post_key)
            .await
    })
}

fn wall_load_avatar_bytes(
    session: FfiWallSession,
    wall_id: String,
    object_key: String,
) -> Result<Vec<u8>, FfiError> {
    block_on_wall(async move {
        let ctx = open_wall_ctx(&session)
            .map_err(|err| entegram_wall::error::WallError::InvalidInput(err.to_string()))?;
        let wall_key = ctx.resolve_wall_key(&wall_id).await?.ok_or_else(|| {
            entegram_wall::error::WallError::InvalidInput(format!("no access to wall {wall_id}"))
        })?;
        ctx.download_decrypted_asset(&wall_id, &object_key, &wall_key)
            .await
    })
}

fn generate_keys_impl(
    password: &str,
    strength: KeyDerivationStrength,
) -> Result<FfiKeyGenResult, FfiError> {
    crypto::init()?;
    let result = auth::generate_keys_with_strength(password, strength)?;
    Ok(FfiKeyGenResult {
        key_attributes: result.key_attributes.into(),
        master_key_b64: result.private_key_attributes.key.to_string(),
        recovery_key_hex: result.private_key_attributes.recovery_key.to_string(),
        secret_key_b64: result.private_key_attributes.secret_key.to_string(),
        key_encryption_key: result.key_encryption_key.to_vec(),
        login_key: result.login_key.to_vec(),
    })
}

fn generate_keys(password: String) -> Result<FfiKeyGenResult, FfiError> {
    generate_keys_impl(&password, KeyDerivationStrength::Sensitive)
}

fn generate_keys_interactive(password: String) -> Result<FfiKeyGenResult, FfiError> {
    generate_keys_impl(&password, KeyDerivationStrength::Interactive)
}

fn derive_kek(
    password: String,
    kek_salt_b64: String,
    mem_limit: u32,
    ops_limit: u32,
) -> Result<Vec<u8>, FfiError> {
    crypto::init()?;
    let kek = auth::derive_kek(&password, &kek_salt_b64, mem_limit, ops_limit)?;
    Ok(kek.to_vec())
}

fn derive_login_key(kek: Vec<u8>) -> Result<Vec<u8>, FfiError> {
    use ente_core::crypto::kdf;
    let login_key = kdf::derive_login_key_secure(&kek)?;
    Ok(login_key.to_vec())
}

fn generate_sensitive_kek(password: String) -> Result<FfiGeneratedKek, FfiError> {
    crypto::init()?;
    let generated = auth::generate_sensitive_kek(&password)?;
    Ok(FfiGeneratedKek {
        key: generated.key.to_vec(),
        salt: generated.salt,
        mem_limit: generated.mem_limit,
        ops_limit: generated.ops_limit,
    })
}

fn generate_interactive_kek(password: String) -> Result<FfiGeneratedKek, FfiError> {
    crypto::init()?;
    let generated = auth::generate_interactive_kek(&password)?;
    Ok(FfiGeneratedKek {
        key: generated.key.to_vec(),
        salt: generated.salt,
        mem_limit: generated.mem_limit,
        ops_limit: generated.ops_limit,
    })
}

fn decrypt_keys(kek: Vec<u8>, key_attrs: FfiKeyAttributes) -> Result<FfiDecryptedKeys, FfiError> {
    let ka: KeyAttributes = (&key_attrs).into();
    let (master_key, secret_key) = auth::decrypt_keys_only(&kek, &ka)?;
    Ok(FfiDecryptedKeys {
        master_key: master_key.to_vec(),
        secret_key: secret_key.to_vec(),
    })
}

fn decrypt_secrets(
    kek: Vec<u8>,
    key_attrs: FfiKeyAttributes,
    encrypted_token: String,
) -> Result<FfiDecryptedSecrets, FfiError> {
    let ka: KeyAttributes = (&key_attrs).into();
    let secrets = auth::decrypt_secrets(&kek, &ka, &encrypted_token)?;
    Ok(FfiDecryptedSecrets {
        master_key: secrets.master_key.to_vec(),
        secret_key: secrets.secret_key.to_vec(),
        token: secrets.token.to_vec(),
    })
}

fn generate_srp_setup(kek: Vec<u8>, srp_user_id: String) -> Result<FfiSrpSetup, FfiError> {
    crypto::init()?;
    let setup = auth::generate_srp_setup(&kek, &srp_user_id)?;
    Ok(FfiSrpSetup {
        srp_salt: setup.srp_salt,
        srp_verifier: setup.srp_verifier,
        login_sub_key: setup.login_sub_key.to_vec(),
    })
}

// --- Secretbox ---

fn encrypt_secretbox_packed(key: Vec<u8>, plaintext: Vec<u8>) -> Result<Vec<u8>, FfiError> {
    Ok(wall_crypto::encrypt_secretbox_packed(&key, &plaintext)?)
}

fn decrypt_secretbox_packed(key: Vec<u8>, packed: Vec<u8>) -> Result<Vec<u8>, FfiError> {
    Ok(wall_crypto::decrypt_secretbox_packed(&key, &packed)?)
}

fn encrypt_secretbox_split(
    key: Vec<u8>,
    plaintext: Vec<u8>,
) -> Result<FfiSplitCiphertext, FfiError> {
    let (ciphertext, nonce) = wall_crypto::encrypt_secretbox_split(&key, &plaintext)?;
    Ok(FfiSplitCiphertext { ciphertext, nonce })
}

fn decrypt_secretbox_split(
    key: Vec<u8>,
    ciphertext: Vec<u8>,
    nonce: Vec<u8>,
) -> Result<Vec<u8>, FfiError> {
    Ok(wall_crypto::decrypt_secretbox_split(
        &key,
        &ciphertext,
        &nonce,
    )?)
}

// --- Asset encryption ---

fn encrypt_asset_payload(key: Vec<u8>, plaintext: Vec<u8>) -> Result<Vec<u8>, FfiError> {
    Ok(wall_crypto::encrypt_asset_payload(&key, &plaintext)?)
}

fn decrypt_asset_payload(key: Vec<u8>, payload: Vec<u8>) -> Result<Vec<u8>, FfiError> {
    Ok(wall_crypto::decrypt_asset_payload(&key, &payload)?)
}

// --- Entity keys ---

fn encrypt_entity_key(
    master_key: Vec<u8>,
    plaintext: Vec<u8>,
) -> Result<FfiEntityKeyPayload, FfiError> {
    let payload = wall_crypto::encrypt_entity_key(&master_key, &plaintext)?;
    Ok(FfiEntityKeyPayload {
        encrypted_key: payload.encrypted_key,
        nonce: payload.header,
    })
}

fn decrypt_entity_key(
    master_key: Vec<u8>,
    payload: FfiEntityKeyPayload,
) -> Result<Vec<u8>, FfiError> {
    let wall_payload = entegram_wall::EntityKeyPayload {
        encrypted_key: payload.encrypted_key,
        header: payload.nonce,
    };
    Ok(wall_crypto::decrypt_entity_key(&master_key, &wall_payload)?)
}

// --- Sealed box ---

fn seal_for_public_key(
    recipient_public_key: Vec<u8>,
    plaintext: Vec<u8>,
) -> Result<Vec<u8>, FfiError> {
    Ok(sealed::seal(&plaintext, &recipient_public_key)?)
}

fn open_sealed_box(
    public_key: Vec<u8>,
    secret_key: Vec<u8>,
    ciphertext: Vec<u8>,
) -> Result<Vec<u8>, FfiError> {
    Ok(sealed::open(&ciphertext, &public_key, &secret_key)?)
}

// --- Base64 ---

fn encode_b64(data: Vec<u8>) -> String {
    wall_crypto::encode_b64(&data)
}

fn decode_b64(encoded: String) -> Result<Vec<u8>, FfiError> {
    wall_crypto::decode_b64(&encoded).map_err(|e| FfiError::Decode { msg: e.to_string() })
}

fn encode_b64_url(data: Vec<u8>) -> String {
    wall_crypto::encode_b64_url(&data)
}

fn decode_b64_url(encoded: String) -> Result<Vec<u8>, FfiError> {
    wall_crypto::decode_b64_url(&encoded).map_err(|e| FfiError::Decode { msg: e.to_string() })
}

// --- Utilities ---

fn derive_labeled_key(secret: Vec<u8>, label: String) -> Vec<u8> {
    wall_crypto::derive_labeled_key(&secret, &label)
}

fn pack_payload(ciphertext: Vec<u8>, nonce: Vec<u8>) -> Vec<u8> {
    wall_crypto::pack_payload(&ciphertext, &nonce)
}

fn unpack_payload(packed: Vec<u8>) -> Result<FfiUnpackedPayload, FfiError> {
    let (ciphertext, nonce) = wall_crypto::unpack_payload(&packed)
        .map_err(|e| FfiError::InvalidInput { msg: e.to_string() })?;
    Ok(FfiUnpackedPayload { ciphertext, nonce })
}

fn generate_key() -> Vec<u8> {
    wall_crypto::generate_key()
}
