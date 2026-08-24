# ente-location-dataset

This host-only package builds the single offline location asset consumed by
`ente-location`. It downloads pinned upstream inputs by default or accepts five
already-extracted source files.

```sh
cargo run --release -p ente-location-dataset -- \
  build --output /tmp/ente-locations.eli
```

Local generation uses `--cities`, `--country-info`, `--countries`, `--disputes`,
and `--admin1` together. The Natural Earth arguments name `.shp` files with
their matching `.dbf` and `.shx` files beside them.

The default sources are:

- GeoNames `cities5000.zip` and `countryInfo.txt`, snapshot 2026-08-23,
  distributed under CC BY 4.0. Ente must retain GeoNames attribution when the
  generated city data is distributed. The city index retains current populated
  place types, including localities, and omits historical, abandoned, and
  destroyed places.
- Natural Earth 5.1.1 Admin-0 Countries, Admin-0 Breakaway and Disputed Areas,
  and Admin-1 States and Provinces, public domain.

URLs and SHA-256 values live in `src/sources.rs`. A changed upstream file fails
closed until its snapshot date and hash are reviewed and updated. Generation is
deterministic: source data and the numeric dataset version determine every byte;
wall-clock timestamps are not stored.
