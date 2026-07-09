use std::fs::File;
use std::io::Read;
use std::path::Path;

use crate::download::{self, Progress, Target};

const MIN_MODEL_BYTES: u64 = 1024 * 1024;

pub fn download_model_files(
    targets: Vec<Target>,
    on_progress: impl FnMut(Progress),
    is_cancelled: impl Fn() -> bool,
) -> Result<(), super::Error> {
    download::fetch(targets, validate_gguf, on_progress, is_cancelled)?;
    Ok(())
}

fn validate_gguf(_target: &Target, path: &Path) -> Result<(), download::Error> {
    if file_size(path).is_none_or(|size| size < MIN_MODEL_BYTES) {
        return Err(download::Error::Validation(format!(
            "{} is too small to be a model file",
            path.display()
        )));
    }
    if !looks_like_gguf(path) {
        return Err(download::Error::Validation(format!(
            "{} is not a valid GGUF file",
            path.display()
        )));
    }
    Ok(())
}

fn looks_like_gguf(path: &Path) -> bool {
    let mut file = match File::open(path) {
        Ok(file) => file,
        Err(_) => return false,
    };
    let mut header = [0u8; 4];
    file.read_exact(&mut header).is_ok() && &header == b"GGUF"
}

fn file_size(path: &Path) -> Option<u64> {
    std::fs::metadata(path).ok().map(|metadata| metadata.len())
}
