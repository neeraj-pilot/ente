use std::path::PathBuf;

use reqwest::blocking::Client;

use crate::Result;
use crate::download::{download, extract};

const CITIES: Source = Source {
    file: "cities5000.zip",
    url: "https://download.geonames.org/export/dump/cities5000.zip",
    sha256: "fa4c55b460cd0eeb4d6d9423849ca2c53df23442578d3ae302b638205cfbb9fd",
};
const COUNTRY_INFO: Source = Source {
    file: "countryInfo.txt",
    url: "https://download.geonames.org/export/dump/countryInfo.txt",
    sha256: "93bafc525813f22e4711ff9ed6d626343094ce48c26388dc7c49189b3d7d5512",
};
const COUNTRIES: Source = Source {
    file: "ne_10m_admin_0_countries.zip",
    url: "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_countries.zip",
    sha256: "ce1ac7036499a0edd641fbc093cd209a98f96a49d2eca8480aaacad35138a7f6",
};
const DISPUTES: Source = Source {
    file: "ne_10m_admin_0_disputed_areas.zip",
    url: "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_disputed_areas.zip",
    sha256: "a250c1cf8ab68898399928a1fa1d5c242eb4de6335cb51a7f7a7f12a9f3c8438",
};
const ADMIN1: Source = Source {
    file: "ne_10m_admin_1_states_provinces.zip",
    url: "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_1_states_provinces.zip",
    sha256: "efc59726337323058f9446210adc96673179cd344e053666ee3d28cb58ba2b05",
};

struct Source {
    file: &'static str,
    url: &'static str,
    sha256: &'static str,
}

#[derive(Clone, Debug)]
pub struct SourcePaths {
    pub cities: PathBuf,
    pub country_info: PathBuf,
    pub countries: PathBuf,
    pub disputes: PathBuf,
    pub admin1: PathBuf,
}

#[derive(Clone, Debug)]
pub struct RemoteSources {
    cache: PathBuf,
}

impl RemoteSources {
    pub fn new(cache: impl Into<PathBuf>) -> Self {
        Self {
            cache: cache.into(),
        }
    }

    pub fn fetch(&self) -> Result<SourcePaths> {
        std::fs::create_dir_all(&self.cache)?;
        let client = Client::builder()
            .user_agent("ente-location-dataset")
            .build()?;
        let cities_archive = self.fetch_source(&client, CITIES)?;
        let country_info = self.fetch_source(&client, COUNTRY_INFO)?;
        let countries_archive = self.fetch_source(&client, COUNTRIES)?;
        let disputes_archive = self.fetch_source(&client, DISPUTES)?;
        let admin1_archive = self.fetch_source(&client, ADMIN1)?;

        let cities_dir = self.cache.join("cities5000");
        extract(&cities_archive, &cities_dir, &["cities5000.txt"])?;
        let countries_dir = self.cache.join("ne_10m_admin_0_countries");
        extract(
            &countries_archive,
            &countries_dir,
            &natural_earth_files("ne_10m_admin_0_countries"),
        )?;
        let disputes_dir = self.cache.join("ne_10m_admin_0_disputed_areas");
        extract(
            &disputes_archive,
            &disputes_dir,
            &natural_earth_files("ne_10m_admin_0_disputed_areas"),
        )?;
        let admin1_dir = self.cache.join("ne_10m_admin_1_states_provinces");
        extract(
            &admin1_archive,
            &admin1_dir,
            &natural_earth_files("ne_10m_admin_1_states_provinces"),
        )?;

        Ok(SourcePaths {
            cities: cities_dir.join("cities5000.txt"),
            country_info,
            countries: countries_dir.join("ne_10m_admin_0_countries.shp"),
            disputes: disputes_dir.join("ne_10m_admin_0_disputed_areas.shp"),
            admin1: admin1_dir.join("ne_10m_admin_1_states_provinces.shp"),
        })
    }

    fn fetch_source(&self, client: &Client, source: Source) -> Result<PathBuf> {
        let path = self.cache.join(source.file);
        download(client, source.url, source.sha256, &path)?;
        Ok(path)
    }
}

fn natural_earth_files(name: &str) -> [String; 3] {
    ["shp", "shx", "dbf"].map(|extension| format!("{name}.{extension}"))
}
