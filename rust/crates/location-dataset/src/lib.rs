use std::fmt::Write as _;
use std::path::PathBuf;

use ente_location::{CityIndex, Coordinate, CountryCode, CountryIndex, CountryView, TerritoryId};
use sha2::{Digest, Sha256};
use thiserror::Error;

mod catalog;
mod city;
mod country;
mod dispute;
mod download;
mod sources;

pub use sources::{RemoteSources, SourcePaths};

pub const CITY_FILE: &str = "cities.bin";
pub const COUNTRY_FILE: &str = "countries.bin";
pub const DISPUTE_FILE: &str = "disputes.bin";

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
    #[error("invalid location data: {0}")]
    InvalidData(String),
    #[error(transparent)]
    Location(#[from] ente_location::Error),
}

pub struct BuildOptions {
    pub sources: SourcePaths,
    pub output: PathBuf,
}

pub struct BuildOutput {
    pub city_count: usize,
    pub territory_count: usize,
    pub byte_length: usize,
    pub files: [BuildFile; 3],
}

pub struct BuildFile {
    pub name: &'static str,
    pub byte_length: usize,
    pub sha256: String,
}

pub fn build(options: &BuildOptions) -> Result<BuildOutput> {
    let cities = city::build(&options.sources.cities, &options.sources.country_info)?;
    let countries = country::build_countries(&options.sources.countries)?;
    let disputes = dispute::build(
        &options.sources.countries,
        &options.sources.disputes,
        &options.sources.admin1,
    )?;
    let city_index = CityIndex::from_bytes(cities.as_slice())?;
    let country_index = CountryIndex::from_bytes(countries.as_slice(), disputes.as_slice())?;
    validate_output(&city_index, &country_index)?;
    let files = [
        build_file(CITY_FILE, &cities),
        build_file(COUNTRY_FILE, &countries),
        build_file(DISPUTE_FILE, &disputes),
    ];
    let output = BuildOutput {
        city_count: city_index.len(),
        territory_count: catalog::DISPUTED_AREAS.len() + catalog::UKRAINIAN_REGIONS.len(),
        byte_length: files.iter().map(|file| file.byte_length).sum(),
        files,
    };
    std::fs::create_dir_all(&options.output)?;
    for (name, bytes) in [
        (CITY_FILE, cities.as_slice()),
        (COUNTRY_FILE, countries.as_slice()),
        (DISPUTE_FILE, disputes.as_slice()),
    ] {
        std::fs::write(options.output.join(name), bytes)?;
    }
    Ok(output)
}

fn validate_output(cities: &CityIndex, countries: &CountryIndex) -> Result<()> {
    for (name, coordinate, expected) in [
        ("Delhi", Coordinate::new(28.6139, 77.2090), *b"IN"),
        ("Beijing", Coordinate::new(39.9042, 116.4074), *b"CN"),
        ("London", Coordinate::new(51.5074, -0.1278), *b"GB"),
    ] {
        let expected = CountryCode::from_bytes(expected).expect("static country code");
        let result = countries.lookup(coordinate)?;
        if !result.disputes.is_empty() || !result.countries.contains(&expected) {
            return Err(invalid(format!("{name} country probe did not match")));
        }
    }
    let ocean = countries.lookup(Coordinate::new(0.0, -140.0))?;
    if !ocean.countries.is_empty() || !ocean.disputes.is_empty() {
        return Err(invalid("ocean country probe matched land"));
    }

    let aksai = countries.lookup(Coordinate::new(35.2, 79.5))?;
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

    let city = cities
        .match_coordinates(&[Coordinate::new(28.6139, 77.2090)], "")
        .pop()
        .ok_or_else(|| invalid("Delhi city probe did not match"))?;
    if city.city.country_code != india {
        return Err(invalid("Delhi city probe matched the wrong country"));
    }
    Ok(())
}

fn build_file(name: &'static str, bytes: &[u8]) -> BuildFile {
    BuildFile {
        name,
        byte_length: bytes.len(),
        sha256: hex(Sha256::digest(bytes)),
    }
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
