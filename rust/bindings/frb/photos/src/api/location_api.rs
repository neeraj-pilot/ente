use ente_location as core;
use flutter_rust_bridge::frb;

pub struct LocationCoordinate {
    pub latitude: f64,
    pub longitude: f64,
}

impl From<LocationCoordinate> for core::Coordinate {
    fn from(coordinate: LocationCoordinate) -> Self {
        Self::new(coordinate.latitude, coordinate.longitude)
    }
}

pub enum LocationCountryView {
    Neutral,
    SourceDefault,
    Region { country_code: String },
}

impl TryFrom<LocationCountryView> for core::CountryView {
    type Error = String;

    fn try_from(view: LocationCountryView) -> Result<Self, Self::Error> {
        Ok(match view {
            LocationCountryView::Neutral => Self::Neutral,
            LocationCountryView::SourceDefault => Self::SourceDefault,
            LocationCountryView::Region { country_code } => Self::Region(
                country_code
                    .parse()
                    .map_err(|error| format!("invalid region country code: {error}"))?,
            ),
        })
    }
}

pub struct LocationCountryGrouping {
    pub countries: Vec<LocationCountryGroup>,
    pub disputes: Vec<LocationDisputeGroup>,
    pub unclassified_coordinate_indices: Vec<u32>,
}

pub struct LocationCountryGroup {
    pub country_code: String,
    pub coordinate_indices: Vec<u32>,
}

pub struct LocationDisputeGroup {
    pub territory_id: u16,
    pub name: String,
    pub possible_country_codes: Vec<String>,
    pub resolved_country_code: Option<String>,
    pub coordinate_indices: Vec<u32>,
}

impl From<core::CountryGrouping> for LocationCountryGrouping {
    fn from(grouping: core::CountryGrouping) -> Self {
        Self {
            countries: grouping
                .countries
                .into_iter()
                .map(|group| LocationCountryGroup {
                    country_code: group.country.to_string(),
                    coordinate_indices: group.coordinate_indices,
                })
                .collect(),
            disputes: grouping
                .disputes
                .into_iter()
                .map(|group| LocationDisputeGroup {
                    territory_id: group.territory.get(),
                    name: group.name,
                    possible_country_codes: group
                        .possible_countries
                        .into_iter()
                        .map(|country| country.to_string())
                        .collect(),
                    resolved_country_code: group
                        .resolved_country
                        .map(|country| country.to_string()),
                    coordinate_indices: group.coordinate_indices,
                })
                .collect(),
            unclassified_coordinate_indices: grouping.unclassified_coordinate_indices,
        }
    }
}

#[frb(opaque)]
pub struct LocationIndex {
    inner: core::LocationIndex,
}

pub fn open_location_index(path: String) -> Result<LocationIndex, String> {
    core::LocationIndex::from_path(path)
        .map(|inner| LocationIndex { inner })
        .map_err(|error| error.to_string())
}

impl LocationIndex {
    pub fn group_countries(
        &self,
        coordinates: Vec<LocationCoordinate>,
        view: LocationCountryView,
    ) -> Result<LocationCountryGrouping, String> {
        let coordinates: Vec<_> = coordinates.into_iter().map(Into::into).collect();
        self.inner
            .group_countries(&coordinates, view.try_into()?)
            .map(Into::into)
            .map_err(|error| error.to_string())
    }
}
