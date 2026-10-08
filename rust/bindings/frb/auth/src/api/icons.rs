use flutter_rust_bridge::frb;

#[frb(non_opaque)]
#[derive(Debug, thiserror::Error)]
#[error("{message}")]
pub struct IconError {
    pub message: String,
}

impl From<ente_icons::Error> for IconError {
    fn from(error: ente_icons::Error) -> Self {
        Self {
            message: error.to_string(),
        }
    }
}

#[frb(opaque)]
pub struct IconFetcher(crate::icons::Fetcher);

impl IconFetcher {
    #[frb(sync)]
    pub fn new() -> Result<Self, IconError> {
        Ok(Self(crate::icons::Fetcher::new()?))
    }

    #[frb(sync)]
    pub fn request(&self) -> IconRequest {
        IconRequest(self.0.request())
    }
}

#[frb(opaque)]
pub struct IconRequest(crate::icons::Request);

impl IconRequest {
    #[frb(sync)]
    pub fn cancel(&self) {
        self.0.cancel();
    }

    pub async fn fetch(&self, url: String) -> Result<Option<Vec<u8>>, IconError> {
        Ok(self.0.fetch(&url).await?)
    }
}
