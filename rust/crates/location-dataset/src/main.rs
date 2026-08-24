use std::env;
use std::error::Error;
use std::path::PathBuf;

use ente_location_dataset::{
    BuildOptions, DEFAULT_DATASET_VERSION, RemoteSources, SourcePaths, build,
};

const USAGE: &str = concat!(
    "usage: ente-location-dataset build --output <file> [--cache <directory>] ",
    "[--version <number>] [--cities <file> --country-info <file> ",
    "--countries <shp> --disputes <shp> --admin1 <shp>]"
);

fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let Arguments {
        output,
        cache,
        version,
        cities,
        country_info,
        countries,
        disputes,
        admin1,
    } = Arguments::parse()?;
    let sources = match (cities, country_info, countries, disputes, admin1) {
        (None, None, None, None, None) => RemoteSources::new(cache).fetch()?,
        (Some(cities), Some(country_info), Some(countries), Some(disputes), Some(admin1)) => {
            SourcePaths {
                cities,
                country_info,
                countries,
                disputes,
                admin1,
            }
        }
        _ => return Err("local generation requires all five source paths".into()),
    };
    let manifest = build(&BuildOptions {
        dataset_version: version,
        sources,
        output,
    })?;
    println!("dataset version: {}", manifest.dataset_version);
    println!("cities: {}", manifest.city_count);
    println!("Priority-1 territories: {}", manifest.territory_count);
    println!("bytes: {}", manifest.byte_length);
    println!("sha256: {}", manifest.sha256);
    Ok(())
}

struct Arguments {
    output: PathBuf,
    cache: PathBuf,
    version: u32,
    cities: Option<PathBuf>,
    country_info: Option<PathBuf>,
    countries: Option<PathBuf>,
    disputes: Option<PathBuf>,
    admin1: Option<PathBuf>,
}

impl Arguments {
    fn parse() -> Result<Self, Box<dyn Error>> {
        let mut args = env::args().skip(1);
        if args.next().as_deref() != Some("build") {
            return Err(USAGE.into());
        }
        let mut output = None;
        let mut cache = None;
        let mut version = DEFAULT_DATASET_VERSION;
        let mut cities = None;
        let mut country_info = None;
        let mut countries = None;
        let mut disputes = None;
        let mut admin1 = None;
        while let Some(argument) = args.next() {
            let value = args.next().ok_or(USAGE)?;
            match argument.as_str() {
                "--output" => output = Some(value.into()),
                "--cache" => cache = Some(value.into()),
                "--version" => version = value.parse()?,
                "--cities" => cities = Some(value.into()),
                "--country-info" => country_info = Some(value.into()),
                "--countries" => countries = Some(value.into()),
                "--disputes" => disputes = Some(value.into()),
                "--admin1" => admin1 = Some(value.into()),
                _ => return Err(USAGE.into()),
            }
        }
        Ok(Self {
            output: output.ok_or(USAGE)?,
            cache: cache.unwrap_or_else(default_cache),
            version,
            cities,
            country_info,
            countries,
            disputes,
            admin1,
        })
    }
}

fn default_cache() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../target/location-dataset/sources")
}
