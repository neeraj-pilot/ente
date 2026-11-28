use serde::Serialize;
use serde_json::Value;

use crate::{Error, Result};

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct VersionedJson {
    pub version: i64,
    pub data: Value,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct FileDocuments {
    pub metadata: Value,
    pub pub_magic_metadata: Option<VersionedJson>,
    pub magic_metadata: Option<VersionedJson>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CollectionDocuments {
    pub private: Option<VersionedJson>,
    pub public: Option<VersionedJson>,
    pub shared: Option<VersionedJson>,
}

pub(crate) fn parse_json(bytes: &[u8]) -> Result<Value> {
    let value: Value = serde_json::from_slice(bytes)?;
    if !value.is_object() {
        return Err(Error::MetadataNotObject);
    }
    Ok(value)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_non_object_json() {
        assert!(parse_json(br#"{"title":"doc.pdf"}"#).is_ok());
        for payload in [b"[]".as_slice(), b"null", b"\"str\"", b"123", b"true"] {
            assert!(matches!(parse_json(payload), Err(Error::MetadataNotObject)));
        }
    }
}
