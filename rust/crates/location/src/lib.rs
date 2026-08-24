use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

mod binary;
mod city;
pub mod cluster;
mod container;
mod country;
mod dispute;
mod error;

pub use city::{City, CityIndex, CityMatch};
pub use country::{CountryCode, CountryIndex, InvalidCountryCode};
pub use dispute::{
    CountryView, DisputeFamily, DisputeIndex, DisputeKind, DisputeMatch, TerritoryId,
};
pub use error::{Error, Result};

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Coordinate {
    pub latitude: f64,
    pub longitude: f64,
}

impl Coordinate {
    pub const fn new(latitude: f64, longitude: f64) -> Self {
        Self {
            latitude,
            longitude,
        }
    }

    pub fn is_valid(self) -> bool {
        self.latitude.is_finite()
            && (-90.0..=90.0).contains(&self.latitude)
            && self.longitude.is_finite()
            && (-180.0..=180.0).contains(&self.longitude)
    }
}

pub struct LocationIndex {
    dataset_version: u32,
    cities: CityIndex,
    countries: CountryIndex,
    disputes: DisputeIndex,
}

impl LocationIndex {
    pub fn from_path(path: impl AsRef<Path>) -> Result<Self> {
        Self::from_bytes(fs::read(path)?)
    }

    pub fn from_bytes(bytes: impl AsRef<[u8]>) -> Result<Self> {
        let sections = container::split(bytes.as_ref())?;
        let cities = CityIndex::from_bytes(sections.cities)?;
        let countries = CountryIndex::from_bytes(sections.countries)?;
        let disputes = DisputeIndex::from_bytes(sections.disputes)?;
        if countries.columns() != 360 || countries.rows() != 180 {
            return Err(Error::invalid(
                "location asset",
                "country grid must be 360 by 180",
            ));
        }
        Ok(Self {
            dataset_version: sections.version,
            cities,
            countries,
            disputes,
        })
    }

    pub const fn dataset_version(&self) -> u32 {
        self.dataset_version
    }

    pub const fn cities(&self) -> &CityIndex {
        &self.cities
    }

    pub const fn countries(&self) -> &CountryIndex {
        &self.countries
    }

    pub const fn disputes(&self) -> &DisputeIndex {
        &self.disputes
    }

    pub fn country_name(&self, code: CountryCode) -> Option<&str> {
        self.cities.country_name(code)
    }

    pub fn classify_country(&self, coordinate: Coordinate) -> Result<CountryClassification<'_>> {
        let cell = self
            .countries
            .prepare_cell(coordinate.latitude, coordinate.longitude)?;
        let countries = self.countries.lookup_prepared(cell)?;
        let disputes = self.disputes.lookup_prepared(cell)?;
        Ok(CountryClassification {
            countries,
            disputes,
        })
    }

    pub fn classify_countries(
        &self,
        coordinates: &[Coordinate],
    ) -> Result<Vec<CountryClassification<'_>>> {
        coordinates
            .iter()
            .map(|&coordinate| self.classify_country(coordinate))
            .collect()
    }

    pub fn group_countries(
        &self,
        coordinates: &[Coordinate],
        view: CountryView,
    ) -> Result<CountryGrouping> {
        let mut countries = BTreeMap::<CountryCode, Vec<u32>>::new();
        let mut disputes = BTreeMap::<TerritoryId, DisputeGroup>::new();
        let mut unclassified_coordinate_indices = Vec::new();

        for (coordinate_index, &coordinate) in coordinates.iter().enumerate() {
            let classification = self.classify_country(coordinate)?;
            let coordinate_index = coordinate_index as u32;
            if classification.disputes.is_empty() {
                if classification.countries.is_empty() {
                    unclassified_coordinate_indices.push(coordinate_index);
                }
                for country in classification.countries {
                    countries.entry(country).or_default().push(coordinate_index);
                }
                continue;
            }

            let mut resolved = Vec::new();
            for dispute in classification.disputes {
                let country = dispute.resolve(view);
                disputes
                    .entry(dispute.territory_id())
                    .or_insert_with(|| DisputeGroup {
                        territory: dispute.territory_id(),
                        name: dispute.name().to_owned(),
                        possible_countries: dispute.possible_countries().collect(),
                        resolved_country: country,
                        coordinate_indices: Vec::new(),
                    })
                    .coordinate_indices
                    .push(coordinate_index);
                if let Some(country) = country
                    && !resolved.contains(&country)
                {
                    resolved.push(country);
                }
            }
            for country in resolved {
                countries.entry(country).or_default().push(coordinate_index);
            }
        }

        Ok(CountryGrouping {
            countries: countries
                .into_iter()
                .map(|(country, coordinate_indices)| CountryGroup {
                    country,
                    coordinate_indices,
                })
                .collect(),
            disputes: disputes.into_values().collect(),
            unclassified_coordinate_indices,
        })
    }
}

#[derive(Debug)]
pub struct CountryClassification<'a> {
    pub countries: Vec<CountryCode>,
    pub disputes: Vec<DisputeMatch<'a>>,
}

impl CountryClassification<'_> {
    pub fn resolved_countries(&self, view: CountryView) -> Vec<CountryCode> {
        if self.disputes.is_empty() {
            return self.countries.clone();
        }
        let mut countries = Vec::new();
        for dispute in &self.disputes {
            if let Some(country) = dispute.resolve(view)
                && !countries.contains(&country)
            {
                countries.push(country);
            }
        }
        countries
    }
}

#[derive(Debug, Eq, PartialEq)]
pub struct CountryGrouping {
    pub countries: Vec<CountryGroup>,
    pub disputes: Vec<DisputeGroup>,
    pub unclassified_coordinate_indices: Vec<u32>,
}

#[derive(Debug, Eq, PartialEq)]
pub struct CountryGroup {
    pub country: CountryCode,
    pub coordinate_indices: Vec<u32>,
}

#[derive(Debug, Eq, PartialEq)]
pub struct DisputeGroup {
    pub territory: TerritoryId,
    pub name: String,
    pub possible_countries: Vec<CountryCode>,
    pub resolved_country: Option<CountryCode>,
    pub coordinate_indices: Vec<u32>,
}
