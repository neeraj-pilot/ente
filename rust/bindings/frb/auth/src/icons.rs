use std::sync::Arc;

use tokio::sync::watch;

pub struct Fetcher(Arc<ente_icons::Fetcher>);

impl Fetcher {
    pub fn new() -> Result<Self, ente_icons::Error> {
        Ok(Self(Arc::new(ente_icons::Fetcher::new()?)))
    }

    pub fn request(&self) -> Request {
        Request {
            fetcher: Arc::clone(&self.0),
            cancelled: watch::channel(false).0,
        }
    }
}

pub struct Request {
    fetcher: Arc<ente_icons::Fetcher>,
    cancelled: watch::Sender<bool>,
}

impl Request {
    pub fn cancel(&self) {
        self.cancelled.send_replace(true);
    }

    pub async fn fetch(&self, url: &str) -> Result<Option<Vec<u8>>, ente_icons::Error> {
        let mut cancelled = self.cancelled.subscribe();
        if *cancelled.borrow() {
            return Ok(None);
        }
        tokio::select! {
            biased;
            _ = cancelled.changed() => Ok(None),
            result = self.fetcher.fetch(url) => result.map(|icon| icon.map(|icon| icon.data)),
        }
    }
}
