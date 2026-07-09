use std::collections::HashMap;
use std::fs;
use std::fs::File;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, OnceLock, PoisonError};

use flate2::read::GzDecoder;
use tar::Archive;

use crate::download;
use crate::transcription::{Result, TranscriptionError};

const MODEL_URL: &str = "https://models.ente.io/parakeet-v3-int8.tar.gz";
const MODEL_DIR_NAME: &str = "parakeet-tdt-0.6b-v3-int8";
const MODEL_LABEL: &str = "Transcription model";
const VAD_MODEL_URL: &str = "https://models.ente.io/silero_vad_v4.onnx";
const VAD_MODEL_FILE_NAME: &str = "silero_vad_v4.onnx";
const VAD_LABEL: &str = "Voice activity model";

static DOWNLOAD_LOCKS: OnceLock<Mutex<HashMap<PathBuf, Arc<Mutex<()>>>>> = OnceLock::new();

fn download_lock(models_dir: &Path) -> Arc<Mutex<()>> {
    let locks = DOWNLOAD_LOCKS.get_or_init(|| Mutex::new(HashMap::new()));
    let mut locks = locks.lock().unwrap_or_else(PoisonError::into_inner);
    locks.entry(models_dir.to_path_buf()).or_default().clone()
}

#[derive(Debug, Clone)]
pub enum ModelEvent {
    DownloadProgress {
        downloaded: u64,
        total: u64,
        percentage: f64,
    },
    ExtractionStarted,
    ExtractionCompleted,
    DownloadComplete,
    DownloadError {
        message: String,
    },
}

pub(crate) fn is_model_downloaded(models_dir: impl AsRef<Path>) -> bool {
    let models_dir = models_dir.as_ref();
    model_path(models_dir).is_dir() && is_file_present(vad_model_path(models_dir))
}

pub(crate) fn model_path(models_dir: impl AsRef<Path>) -> PathBuf {
    models_dir.as_ref().join(MODEL_DIR_NAME)
}

pub(crate) fn vad_model_path(models_dir: impl AsRef<Path>) -> PathBuf {
    models_dir.as_ref().join(VAD_MODEL_FILE_NAME)
}

pub(crate) fn download_model(
    models_dir: impl AsRef<Path>,
    mut on_event: impl FnMut(ModelEvent),
) -> Result<PathBuf> {
    let models_dir = models_dir.as_ref();
    let lock = download_lock(models_dir);
    let _guard = lock.lock().unwrap_or_else(PoisonError::into_inner);
    fs::create_dir_all(models_dir)?;
    let _ = fs::remove_file(models_dir.join(format!("{MODEL_DIR_NAME}.partial")));
    let _ = fs::remove_file(models_dir.join(format!("{VAD_MODEL_FILE_NAME}.partial")));

    let final_model_dir = model_path(models_dir);
    let vad_path = vad_model_path(models_dir);
    let archive_path = models_dir.join(format!("{MODEL_DIR_NAME}.tar.gz"));

    if final_model_dir.is_dir() && is_file_present(&vad_path) {
        on_event(ModelEvent::DownloadComplete);
        return Ok(final_model_dir);
    }

    let need_model = !final_model_dir.is_dir();
    let mut targets = Vec::new();
    if need_model {
        targets.push(download::Target {
            label: MODEL_LABEL.to_string(),
            url: MODEL_URL.to_string(),
            destination_path: archive_path.display().to_string(),
        });
    }
    if !is_file_present(&vad_path) {
        targets.push(download::Target {
            label: VAD_LABEL.to_string(),
            url: VAD_MODEL_URL.to_string(),
            destination_path: vad_path.display().to_string(),
        });
    }

    if let Err(err) = download::fetch(
        targets,
        validate_download,
        |progress| {
            on_event(ModelEvent::DownloadProgress {
                downloaded: progress.downloaded_bytes,
                total: progress.total_bytes.unwrap_or(0),
                percentage: progress.percentage,
            });
        },
        || false,
    ) {
        on_event(ModelEvent::DownloadError {
            message: err.to_string(),
        });
        return Err(TranscriptionError::Download(err));
    }

    if need_model {
        on_event(ModelEvent::ExtractionStarted);
        let extracting_path = models_dir.join(format!("{MODEL_DIR_NAME}.extracting"));
        let extract_result = extract_archive(&archive_path, &extracting_path, &final_model_dir);
        let _ = fs::remove_file(&archive_path);
        let _ = fs::remove_file(download::metadata_path_for(&archive_path));
        if let Err(err) = extract_result {
            let _ = fs::remove_dir_all(&extracting_path);
            on_event(ModelEvent::DownloadError {
                message: err.to_string(),
            });
            return Err(err);
        }
        on_event(ModelEvent::ExtractionCompleted);
    }

    on_event(ModelEvent::DownloadComplete);
    Ok(final_model_dir)
}

fn validate_download(
    target: &download::Target,
    path: &Path,
) -> std::result::Result<(), download::Error> {
    if !is_file_present(path) {
        return Err(download::Error::Validation(format!(
            "{} is empty",
            path.display()
        )));
    }
    if target.destination_path.ends_with(".tar.gz") && !looks_like_gzip(path) {
        return Err(download::Error::Validation(format!(
            "{} is not a gzip archive",
            path.display()
        )));
    }
    Ok(())
}

fn looks_like_gzip(path: &Path) -> bool {
    let mut file = match File::open(path) {
        Ok(file) => file,
        Err(_) => return false,
    };
    let mut header = [0u8; 2];
    file.read_exact(&mut header).is_ok() && header == [0x1f, 0x8b]
}

fn extract_archive(
    archive_path: &Path,
    extracting_path: &Path,
    final_model_dir: &Path,
) -> Result<()> {
    if extracting_path.exists() {
        fs::remove_dir_all(extracting_path)?;
    }
    fs::create_dir_all(extracting_path)?;

    let tar_gz = File::open(archive_path)?;
    let tar = GzDecoder::new(tar_gz);
    let mut archive = Archive::new(tar);
    archive.unpack(extracting_path)?;

    let extracted_dirs = fs::read_dir(extracting_path)?
        .filter_map(|entry| entry.ok())
        .filter(|entry| {
            entry
                .file_type()
                .map(|file_type| file_type.is_dir())
                .unwrap_or(false)
        })
        .collect::<Vec<_>>();

    if final_model_dir.exists() {
        fs::remove_dir_all(final_model_dir)?;
    }

    if extracted_dirs.len() == 1 {
        fs::rename(extracted_dirs[0].path(), final_model_dir)?;
        let _ = fs::remove_dir_all(extracting_path);
    } else {
        fs::rename(extracting_path, final_model_dir)?;
    }

    Ok(())
}

fn is_file_present(path: impl AsRef<Path>) -> bool {
    path.as_ref()
        .metadata()
        .map(|metadata| metadata.is_file() && metadata.len() > 0)
        .unwrap_or(false)
}
