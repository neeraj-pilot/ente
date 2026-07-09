use std::path::{Path, PathBuf};
use std::sync::{Mutex, MutexGuard, PoisonError};

use transcribe_rs::onnx::Quantization;
use transcribe_rs::onnx::parakeet::{ParakeetModel, ParakeetParams, TimestampGranularity};

use crate::transcription::audio::extract_speech_from_pcm16;
use crate::transcription::model::{self, ModelEvent};
use crate::transcription::text::filter_transcription_output;
use crate::transcription::{Result, TranscriptionError};

pub struct Transcriber {
    models_dir: PathBuf,
    loaded: Mutex<Option<ParakeetModel>>,
}

impl Transcriber {
    pub fn new(models_dir: impl Into<PathBuf>) -> Self {
        Self {
            models_dir: models_dir.into(),
            loaded: Mutex::new(None),
        }
    }

    pub fn is_model_downloaded(&self) -> bool {
        model::is_model_downloaded(&self.models_dir)
    }

    pub fn download_model(&self, on_event: impl FnMut(ModelEvent)) -> Result<PathBuf> {
        model::download_model(&self.models_dir, on_event)
    }

    pub fn load_model(&self) -> Result<()> {
        let model_dir = self.downloaded_model_dir()?;
        ensure_loaded(&mut self.lock(), &model_dir)?;
        Ok(())
    }

    pub fn unload_model(&self) {
        *self.lock() = None;
    }

    pub fn transcribe(&self, input_sample_rate: u32, pcm_le: Vec<u8>) -> Result<String> {
        if pcm_le.is_empty() {
            return Ok(String::new());
        }

        let model_dir = self.downloaded_model_dir()?;
        let vad_model_path = model::vad_model_path(&self.models_dir);
        if !vad_model_path.is_file() {
            return Err(TranscriptionError::VadNotDownloaded);
        }

        let speech = extract_speech_from_pcm16(&vad_model_path, input_sample_rate, &pcm_le)?;
        if speech.is_empty() {
            return Ok(String::new());
        }

        let mut loaded = self.lock();
        let model = ensure_loaded(&mut loaded, &model_dir)?;
        let result = model.transcribe_with(
            &speech,
            &ParakeetParams {
                timestamp_granularity: Some(TimestampGranularity::Segment),
                ..Default::default()
            },
        )?;

        Ok(filter_transcription_output(&result.text))
    }

    fn downloaded_model_dir(&self) -> Result<PathBuf> {
        let model_dir = model::model_path(&self.models_dir);
        if !model_dir.is_dir() {
            return Err(TranscriptionError::NotDownloaded);
        }
        Ok(model_dir)
    }

    fn lock(&self) -> MutexGuard<'_, Option<ParakeetModel>> {
        self.loaded.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

fn ensure_loaded<'a>(
    loaded: &'a mut Option<ParakeetModel>,
    model_dir: &Path,
) -> Result<&'a mut ParakeetModel> {
    match loaded {
        Some(model) => Ok(model),
        slot => Ok(slot.insert(ParakeetModel::load(model_dir, &Quantization::Int8)?)),
    }
}
