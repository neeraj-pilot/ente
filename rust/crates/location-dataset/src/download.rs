use std::fs::File;
use std::io::{BufReader, BufWriter, Read, Write};
use std::path::Path;

use reqwest::blocking::Client;
use sha2::{Digest, Sha256};
use zip::ZipArchive;

use crate::{Result, hex, invalid};

pub(crate) fn download(
    client: &Client,
    url: &str,
    expected_sha256: &str,
    path: &Path,
) -> Result<()> {
    if path.is_file() && sha256_file(path)? == expected_sha256 {
        return Ok(());
    }
    let temporary = path.with_extension("download.part");
    let mut response = client.get(url).send()?.error_for_status()?;
    let mut writer = BufWriter::new(File::create(&temporary)?);
    let mut hasher = Sha256::new();
    let mut buffer = [0; 64 * 1024];
    loop {
        let count = response.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
        writer.write_all(&buffer[..count])?;
    }
    writer.flush()?;
    let actual = hex(hasher.finalize());
    if actual != expected_sha256 {
        return Err(invalid(format!(
            "checksum mismatch for {url}: expected {expected_sha256}, got {actual}"
        )));
    }
    if path.exists() {
        std::fs::remove_file(path)?;
    }
    std::fs::rename(temporary, path)?;
    Ok(())
}

pub(crate) fn extract<S: AsRef<str>>(
    archive_path: &Path,
    destination: &Path,
    files: &[S],
) -> Result<()> {
    std::fs::create_dir_all(destination)?;
    let mut archive = ZipArchive::new(File::open(archive_path)?)?;
    for file_name in files {
        let file_name = file_name.as_ref();
        let output = destination.join(file_name);
        let mut source = archive.by_name(file_name)?;
        let temporary = output.with_extension("extract.part");
        let mut writer = BufWriter::new(File::create(&temporary)?);
        std::io::copy(&mut source, &mut writer)?;
        writer.flush()?;
        if output.exists() {
            std::fs::remove_file(&output)?;
        }
        std::fs::rename(temporary, output)?;
    }
    Ok(())
}

fn sha256_file(path: &Path) -> Result<String> {
    let mut reader = BufReader::new(File::open(path)?);
    let mut hasher = Sha256::new();
    let mut buffer = [0; 64 * 1024];
    loop {
        let count = reader.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(hex(hasher.finalize()))
}
