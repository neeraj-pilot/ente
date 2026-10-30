use ente_location::Coordinate;

use super::MAX_MEMORY_ITEMS;
use super::calendar::LocalDate;
use super::photo::Photo;

#[derive(Clone, Debug, PartialEq)]
pub struct SmartMemories {
    pub periods: Vec<Vec<Memory>>,
    pub base_locations: Vec<BaseLocation>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct BaseLocation {
    pub file_ids: Vec<i64>,
    pub location: Coordinate,
    pub is_current: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Memory {
    pub kind: MemoryKind,
    pub file_ids: Vec<i64>,
    pub first_date_to_show: i64,
    pub last_date_to_show: i64,
}

#[derive(Clone, Debug, PartialEq)]
pub enum MemoryKind {
    OnThisDay,
    Trip(TripMemory),
    Base(BaseMemory),
    Time(TimeMemory),
    Filler { years_ago: i32 },
}

#[derive(Clone, Debug, PartialEq)]
pub struct TripMemory {
    pub location: Coordinate,
    pub location_name: Option<String>,
    pub year: i32,
    pub key: String,
}

#[derive(Clone, Debug, PartialEq)]
pub struct BaseMemory {
    pub location: Coordinate,
    pub location_name: Option<String>,
    pub is_current: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TimeMemory {
    Day {
        date: LocalDate,
        years_ago: Option<i32>,
    },
    Week {
        years_ago: Option<i32>,
    },
    Month {
        year: i32,
        month: i32,
        years_ago: Option<i32>,
    },
    LastWeek,
    LastMonth,
}

impl Memory {
    pub(super) fn new(
        kind: MemoryKind,
        photos: &[&Photo],
        first_date_to_show: i64,
        last_date_to_show: i64,
    ) -> Self {
        Self {
            kind,
            file_ids: photos
                .iter()
                .take(MAX_MEMORY_ITEMS)
                .map(|photo| photo.id)
                .collect(),
            first_date_to_show,
            last_date_to_show,
        }
    }

    pub(super) fn is_shown_at(&self, time: i64) -> bool {
        (self.first_date_to_show..=self.last_date_to_show).contains(&time)
    }
}
