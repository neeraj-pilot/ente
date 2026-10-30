use std::collections::{BTreeMap, HashSet};

use super::calendar::MICROS_PER_MINUTE;
use super::input::PhotoSignals;
use super::photo::{Photo, sort_chronologically};
use super::rng::Rng;

const DEFAULT_SELECTION_SIZE: usize = 10;
const MINIMUM_TIME_GAP: i64 = 10 * MICROS_PER_MINUTE;
const NEAR_DUPLICATE_SIMILARITY: f32 = 0.80;
const FACE_WEIGHT_SCORE: f64 = 1000.0;

pub(super) struct Selector<'s> {
    signals: Option<&'s dyn PhotoSignals>,
    rng: Rng,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Spread {
    None,
    OverTime,
    AcrossYears,
}

#[derive(Clone, Copy)]
struct Candidate<'p> {
    photo: &'p Photo,
    score: f64,
}

impl<'s> Selector<'s> {
    pub(super) fn new(signals: Option<&'s dyn PhotoSignals>, seed: u64) -> Self {
        Self {
            signals,
            rng: Rng::new(seed),
        }
    }

    pub(super) fn best<'p>(
        &mut self,
        photos: Vec<&'p Photo>,
        preferred_size: Option<usize>,
    ) -> Vec<&'p Photo> {
        self.select(photos, preferred_size, false)
    }

    pub(super) fn best_over_time<'p>(
        &mut self,
        photos: Vec<&'p Photo>,
        size: usize,
    ) -> Vec<&'p Photo> {
        self.select(photos, Some(size), true)
    }

    pub(super) fn sample_across_years<'p>(
        &mut self,
        mut photos: Vec<&'p Photo>,
        size: usize,
    ) -> Vec<&'p Photo> {
        sort_chronologically(&mut photos);
        if photos.len() <= size {
            return photos;
        }
        let mut years: Vec<Vec<&Photo>> = by_year(&photos).into_values().collect();
        for year in &mut years {
            self.rng.shuffle(year);
        }
        if years.len() > size {
            self.rng.shuffle(&mut years);
            years.truncate(size);
            years.sort_by_key(|year| year[0].date.year);
        }
        let mut selected = round_robin(&years, size, |_, _, _| true);
        sort_chronologically(&mut selected);
        selected
    }

    fn select<'p>(
        &mut self,
        mut photos: Vec<&'p Photo>,
        preferred_size: Option<usize>,
        over_time: bool,
    ) -> Vec<&'p Photo> {
        sort_chronologically(&mut photos);
        let mut size = preferred_size.unwrap_or(DEFAULT_SELECTION_SIZE);
        if photos.len() <= size {
            return photos;
        }
        let year_count = photos
            .iter()
            .map(|photo| photo.date.year)
            .collect::<HashSet<_>>()
            .len();
        let spread = if over_time {
            Spread::OverTime
        } else if year_count <= 1 {
            Spread::None
        } else {
            if (preferred_size.is_none() || self.signals.is_none())
                && year_count * 2 > DEFAULT_SELECTION_SIZE
            {
                size = year_count * 3;
                if photos.len() < size {
                    return photos;
                }
            }
            Spread::AcrossYears
        };
        let mut selected = match self.signals {
            Some(signals) => ranked(&photos, size, spread, signals),
            None => self.random(&photos, size, spread),
        };
        sort_chronologically(&mut selected);
        selected
    }

    fn random<'p>(&mut self, photos: &[&'p Photo], size: usize, spread: Spread) -> Vec<&'p Photo> {
        if spread != Spread::AcrossYears {
            return self.random_over_time(photos, size);
        }
        let mut years: Vec<Vec<&Photo>> = by_year(photos).into_values().rev().collect();
        for year in &mut years {
            self.rng.shuffle(year);
        }
        round_robin(&years, size, |_, photo, selected| {
            !is_too_close(photo, selected)
        })
    }

    fn random_over_time<'p>(&mut self, photos: &[&'p Photo], size: usize) -> Vec<&'p Photo> {
        if is_single_moment(photos, |photo| photo.creation_time) {
            let mut shuffled = photos.to_vec();
            self.rng.shuffle(&mut shuffled);
            shuffled.truncate(size);
            return shuffled;
        }
        let mut selected = Vec::with_capacity(size);
        for mut bucket in time_buckets(photos, size, |photo| photo.creation_time) {
            self.rng.shuffle(&mut bucket);
            if let Some(photo) = bucket
                .into_iter()
                .find(|photo| !is_too_close(photo, &selected))
            {
                selected.push(photo);
            }
        }
        for photo in photos {
            if selected.len() >= size {
                break;
            }
            if !selected.iter().any(|chosen| chosen.id == photo.id)
                && !is_too_close(photo, &selected)
            {
                selected.push(photo);
            }
        }
        selected
    }
}

fn ranked<'p>(
    photos: &[&'p Photo],
    size: usize,
    spread: Spread,
    signals: &dyn PhotoSignals,
) -> Vec<&'p Photo> {
    let candidates: Vec<Candidate> = photos
        .iter()
        .map(|&photo| Candidate {
            photo,
            score: f64::from(signals.face_weight(photo.id)) * FACE_WEIGHT_SCORE
                + signals.nostalgia(photo.id).map_or(0.0, f64::from),
        })
        .collect();
    match spread {
        Spread::None => ranked_flat(candidates, size, signals),
        Spread::OverTime => ranked_over_time(candidates, size, signals),
        Spread::AcrossYears => ranked_across_years(candidates, size, signals),
    }
}

fn ranked_flat<'p>(
    mut candidates: Vec<Candidate<'p>>,
    size: usize,
    signals: &dyn PhotoSignals,
) -> Vec<&'p Photo> {
    sort_by_score(&mut candidates);
    let total = candidates.len();
    let mut selected: Vec<&Photo> = Vec::with_capacity(size);
    let mut skipped = 0;
    for candidate in candidates {
        if selected.len() >= size {
            break;
        }
        let photo = candidate.photo;
        if !selected.is_empty()
            && (is_too_close(photo, &selected)
                || (total - skipped > size && is_near_duplicate(photo, &selected, signals)))
        {
            skipped += 1;
            continue;
        }
        selected.push(photo);
    }
    selected
}

fn ranked_over_time<'p>(
    candidates: Vec<Candidate<'p>>,
    size: usize,
    signals: &dyn PhotoSignals,
) -> Vec<&'p Photo> {
    if is_single_moment(&candidates, |candidate| candidate.photo.creation_time) {
        return ranked_flat(candidates, size, signals);
    }
    let mut selected: Vec<&Photo> = Vec::with_capacity(size);
    for mut bucket in time_buckets(&candidates, size, |candidate| candidate.photo.creation_time) {
        sort_by_score(&mut bucket);
        let distinct: Vec<Candidate> = bucket
            .iter()
            .copied()
            .filter(|candidate| !is_near_duplicate(candidate.photo, &selected, signals))
            .collect();
        let pool = if distinct.is_empty() {
            bucket
        } else {
            distinct
        };
        if let Some(winner) = pool
            .into_iter()
            .find(|candidate| !is_too_close(candidate.photo, &selected))
        {
            selected.push(winner.photo);
        }
    }
    if selected.len() < size {
        let chosen: HashSet<i64> = selected.iter().map(|photo| photo.id).collect();
        let mut rest: Vec<Candidate> = candidates
            .into_iter()
            .filter(|candidate| !chosen.contains(&candidate.photo.id))
            .collect();
        sort_by_score(&mut rest);
        for candidate in rest {
            if selected.len() >= size {
                break;
            }
            if !is_near_duplicate(candidate.photo, &selected, signals)
                && !is_too_close(candidate.photo, &selected)
            {
                selected.push(candidate.photo);
            }
        }
    }
    selected
}

fn ranked_across_years<'p>(
    candidates: Vec<Candidate<'p>>,
    size: usize,
    signals: &dyn PhotoSignals,
) -> Vec<&'p Photo> {
    let total = candidates.len();
    let mut years: BTreeMap<i32, Vec<Candidate>> = BTreeMap::new();
    for candidate in candidates {
        years
            .entry(candidate.photo.date.year)
            .or_default()
            .push(candidate);
    }
    let years: Vec<Vec<&Photo>> = years
        .into_values()
        .rev()
        .map(|mut year| {
            sort_by_score(&mut year);
            year.into_iter().map(|candidate| candidate.photo).collect()
        })
        .collect();
    let mut skipped = 0;
    round_robin(&years, size, |round, photo, selected| {
        let rejected = is_too_close(photo, selected)
            || (round > 0 && total - skipped > size && is_near_duplicate(photo, selected, signals));
        if rejected {
            skipped += 1;
        }
        !rejected
    })
}

fn round_robin<'p>(
    years: &[Vec<&'p Photo>],
    size: usize,
    mut accept: impl FnMut(usize, &Photo, &[&'p Photo]) -> bool,
) -> Vec<&'p Photo> {
    let rounds = years.iter().map(Vec::len).max().unwrap_or(0);
    let mut selected = Vec::with_capacity(size);
    for round in 0..rounds {
        for photo in years.iter().filter_map(|year| year.get(round)) {
            if selected.len() >= size {
                return selected;
            }
            if accept(round, photo, &selected) {
                selected.push(*photo);
            }
        }
    }
    selected
}

fn by_year<'p>(photos: &[&'p Photo]) -> BTreeMap<i32, Vec<&'p Photo>> {
    let mut years: BTreeMap<i32, Vec<&Photo>> = BTreeMap::new();
    for &photo in photos {
        years.entry(photo.date.year).or_default().push(photo);
    }
    years
}

fn time_buckets<T: Copy>(items: &[T], count: usize, time: impl Fn(&T) -> i64) -> Vec<Vec<T>> {
    let mut buckets = vec![Vec::new(); count];
    let (Some(first), Some(last)) = (items.first(), items.last()) else {
        return buckets;
    };
    let start = time(first);
    let range = i128::from(time(last) - start) + 1;
    for item in items {
        let index = i128::from(time(item) - start) * count as i128 / range;
        buckets[(index as usize).min(count - 1)].push(*item);
    }
    buckets
}

fn sort_by_score(candidates: &mut [Candidate]) {
    candidates.sort_by(|a, b| {
        b.score.total_cmp(&a.score).then_with(|| {
            (a.photo.creation_time, a.photo.id).cmp(&(b.photo.creation_time, b.photo.id))
        })
    });
}

fn is_single_moment<T>(items: &[T], time: impl Fn(&T) -> i64) -> bool {
    items.first().map(&time) == items.last().map(&time)
}

fn is_too_close(photo: &Photo, selected: &[&Photo]) -> bool {
    selected
        .iter()
        .any(|other| (photo.creation_time - other.creation_time).abs() < MINIMUM_TIME_GAP)
}

fn is_near_duplicate(photo: &Photo, selected: &[&Photo], signals: &dyn PhotoSignals) -> bool {
    selected.iter().any(|other| {
        signals
            .similarity(photo.id, other.id)
            .is_some_and(|similarity| similarity > NEAR_DUPLICATE_SIMILARITY)
    })
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::Selector;
    use crate::memories::input::{MemoryFile, PhotoSignals};
    use crate::memories::photo::{Photo, prepare};
    use crate::memories::tests::{file, micros};

    #[derive(Default)]
    struct Signals {
        face_weights: HashMap<i64, u32>,
        nostalgia: HashMap<i64, f32>,
        near_duplicates: HashSet<(i64, i64)>,
    }

    impl PhotoSignals for Signals {
        fn face_weight(&self, file_id: i64) -> u32 {
            self.face_weights.get(&file_id).copied().unwrap_or(0)
        }

        fn nostalgia(&self, file_id: i64) -> Option<f32> {
            self.nostalgia.get(&file_id).copied()
        }

        fn similarity(&self, file_id: i64, other_file_id: i64) -> Option<f32> {
            let pair = (file_id.min(other_file_id), file_id.max(other_file_id));
            Some(if self.near_duplicates.contains(&pair) {
                0.95
            } else {
                0.1
            })
        }
    }

    fn daily(first_id: i64, start: &str, count: i64) -> Vec<MemoryFile> {
        let start = micros(start);
        (0..count)
            .map(|index| MemoryFile {
                creation_time: start + index * 86_400_000_000,
                ..file(first_id + index, "1970-01-01T00:00:00")
            })
            .collect()
    }

    fn ids(photos: &[&Photo]) -> Vec<i64> {
        photos.iter().map(|photo| photo.id).collect()
    }

    #[test]
    fn single_year_picks_rank_faces_then_nostalgia_and_skip_duplicates_and_bursts() {
        let mut files = daily(1, "2024-01-01T12:00:00", 15);
        files[12].creation_time = micros("2024-01-12T12:05:00");
        let photos = prepare(&files);
        let signals = Signals {
            face_weights: HashMap::from([(3, 10)]),
            nostalgia: (1..=15).map(|id| (id, id as f32 / 100.0)).collect(),
            near_duplicates: HashSet::from([(14, 15)]),
        };

        let selected = Selector::new(Some(&signals), 0).best(photos.iter().collect(), None);

        assert_eq!(ids(&selected), [3, 5, 6, 7, 8, 9, 10, 11, 13, 15]);
    }

    fn six_years_of_four_photos() -> Vec<MemoryFile> {
        (2015..2021)
            .flat_map(|year| daily(year * 10, &format!("{year}-06-01T12:00:00"), 4))
            .collect()
    }

    fn assert_three_per_year(selected: &[&Photo]) {
        assert_eq!(selected.len(), 18);
        for year in 2015..2021 {
            let from_year = selected
                .iter()
                .filter(|photo| photo.id / 10 == year)
                .count();
            assert_eq!(from_year, 3, "{year}");
        }
    }

    #[test]
    fn many_years_get_three_photos_each_in_turn() {
        let photos = prepare(&six_years_of_four_photos());
        let signals = Signals::default();

        let selected = Selector::new(Some(&signals), 0).best(photos.iter().collect(), None);

        assert_three_per_year(&selected);
    }

    #[test]
    fn random_picks_take_three_per_year_even_when_a_size_is_preferred() {
        let photos = prepare(&six_years_of_four_photos());
        let signals = Signals::default();

        let random = Selector::new(None, 0).best(photos.iter().collect(), Some(20));
        let ranked = Selector::new(Some(&signals), 0).best(photos.iter().collect(), Some(20));

        assert_three_per_year(&random);
        assert_eq!(ranked.len(), 20);
    }

    #[test]
    fn random_picks_spread_over_time_and_follow_the_seed() {
        let photos = prepare(&daily(0, "2024-01-01T12:00:00", 40));
        let pick =
            |seed| ids(&Selector::new(None, seed).best_over_time(photos.iter().collect(), 10));

        let selected = pick(5);

        assert_eq!(selected.len(), 10);
        for bucket in 0..10 {
            assert_eq!(selected[bucket] / 4, bucket as i64, "{selected:?}");
        }
        assert_eq!(selected, pick(5));
        assert_ne!(selected, pick(6));
    }

    #[test]
    fn small_sets_are_kept_whole_in_chronological_order() {
        let photos = prepare(&daily(0, "2024-01-01T12:00:00", 5));
        let reversed: Vec<&Photo> = photos.iter().rev().collect();

        let selected = Selector::new(None, 0).best(reversed, None);

        assert_eq!(ids(&selected), [0, 1, 2, 3, 4]);
    }
}
