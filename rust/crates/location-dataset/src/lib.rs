use std::fmt::Write as _;
use std::path::{Path, PathBuf};

use ente_location::{Coordinate, CountryCode, CountryView, LocationIndex, TerritoryId};
use thiserror::Error;

mod catalog;
mod city;
mod country;
mod dispute;
mod download;
mod format;
mod sources;

pub use sources::{DEFAULT_DATASET_VERSION, RemoteSources, SourcePaths};

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, Error)]
pub enum Error {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("download failed: {0}")]
    Http(#[from] reqwest::Error),
    #[error("ZIP archive is invalid: {0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("shapefile is invalid: {0}")]
    Shapefile(#[from] shapefile::Error),
    #[error("generated manifest is invalid: {0}")]
    Json(#[from] serde_json::Error),
    #[error("invalid location data: {0}")]
    InvalidData(String),
    #[error(transparent)]
    Location(#[from] ente_location::Error),
}

pub struct BuildOptions {
    pub dataset_version: u32,
    pub sources: SourcePaths,
    pub output: PathBuf,
}

pub struct BuildManifest {
    pub dataset_version: u32,
    pub city_count: usize,
    pub territory_count: usize,
    pub byte_length: usize,
    pub sha256: String,
}

pub fn build(options: &BuildOptions) -> Result<BuildManifest> {
    let cities = city::build(&options.sources.cities, &options.sources.country_info)?;
    let countries = country::build_countries(&options.sources.countries)?;
    let disputes = dispute::build(
        &options.sources.countries,
        &options.sources.disputes,
        &options.sources.admin1,
    )?;
    let bytes = format::encode(options.dataset_version, &cities, &countries, &disputes)?;
    let index = LocationIndex::from_bytes(&bytes)?;
    validate_output(&index)?;
    let manifest = BuildManifest {
        dataset_version: options.dataset_version,
        city_count: index.cities().len(),
        territory_count: index.disputes().territory_count(),
        byte_length: bytes.len(),
        sha256: format::sha256(&bytes),
    };
    write_output(&options.output, &bytes)?;
    write_manifest(&options.output, &manifest)?;
    Ok(manifest)
}

fn validate_output(index: &LocationIndex) -> Result<()> {
    if let Some(code) = index
        .countries()
        .country_codes()
        .find(|&code| index.country_name(code).is_none())
    {
        return Err(invalid(format!("country {code} has no display name")));
    }
    for (name, coordinate, expected) in [
        ("Delhi", Coordinate::new(28.6139, 77.2090), *b"IN"),
        ("Beijing", Coordinate::new(39.9042, 116.4074), *b"CN"),
        ("London", Coordinate::new(51.5074, -0.1278), *b"GB"),
    ] {
        let expected = CountryCode::from_bytes(expected).expect("static country code");
        let result = index.classify_country(coordinate)?;
        if !result.disputes.is_empty() || !result.countries.contains(&expected) {
            return Err(invalid(format!("{name} country probe did not match")));
        }
    }
    let ocean = index.classify_country(Coordinate::new(0.0, -140.0))?;
    if !ocean.countries.is_empty() || !ocean.disputes.is_empty() {
        return Err(invalid("ocean country probe matched land"));
    }

    let aksai = index.classify_country(Coordinate::new(35.2, 79.5))?;
    let territory = aksai
        .disputes
        .iter()
        .copied()
        .find(|item| item.territory_id() == TerritoryId::AKSAI_CHIN)
        .ok_or_else(|| invalid("Aksai Chin dispute probe did not match"))?;
    let india = CountryCode::from_bytes(*b"IN").expect("static country code");
    let china = CountryCode::from_bytes(*b"CN").expect("static country code");
    if territory.resolve(CountryView::Neutral).is_some()
        || territory.resolve(CountryView::Region(india)) != Some(india)
        || territory.resolve(CountryView::Region(china)) != Some(china)
    {
        return Err(invalid("Aksai Chin worldview probe did not resolve"));
    }

    let city = index
        .cities()
        .nearest_batch(&[Coordinate::new(28.6139, 77.2090)], 30.0)
        .pop()
        .flatten()
        .ok_or_else(|| invalid("Delhi city probe did not match"))?;
    if city.country_code != india {
        return Err(invalid("Delhi city probe matched the wrong country"));
    }
    Ok(())
}

fn write_output(path: &Path, bytes: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let temporary = path.with_extension("eli.part");
    std::fs::write(&temporary, bytes)?;
    std::fs::rename(temporary, path)?;
    Ok(())
}

fn write_manifest(path: &Path, manifest: &BuildManifest) -> Result<()> {
    let json = serde_json::json!({
        "format": "ELI1",
        "datasetVersion": manifest.dataset_version,
        "cityCount": manifest.city_count,
        "territoryCount": manifest.territory_count,
        "byteLength": manifest.byte_length,
        "sha256": manifest.sha256,
    });
    let mut manifest_path = path.as_os_str().to_os_string();
    manifest_path.push(".manifest.json");
    let path = PathBuf::from(manifest_path);
    std::fs::write(path, format!("{}\n", serde_json::to_string_pretty(&json)?))?;
    Ok(())
}

fn invalid(message: impl Into<String>) -> Error {
    Error::InvalidData(message.into())
}

fn hex(bytes: impl AsRef<[u8]>) -> String {
    let mut output = String::with_capacity(bytes.as_ref().len() * 2);
    for byte in bytes.as_ref() {
        write!(output, "{byte:02x}").expect("writing to a string is infallible");
    }
    output
}
