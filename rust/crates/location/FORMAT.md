# Ente location asset files

All integers and floating-point values are little-endian. Strings are UTF-8.
Readers validate every count, offset, reference, coordinate, and string before
serving queries.

The generator emits three independently downloadable files. Each starts with a
four-byte type tag, format version, and header length. Per-file checksums live
in the app's asset catalog.

## City file

`cities.bin` uses the `CITY` tag and format version 1. It stores
priority-ordered city points and a balanced two-dimensional KD tree. Its
72-byte header contains point/name/country counts, section offsets, record
lengths, the file length, and the cumulative ends of ranks 4 through 1.

The tree is preorder, so each three-byte node needs only its point index. Child
positions and split axes follow from subtree size and depth. Each 15-byte point
contains `f32` latitude/longitude, a three-byte name index, a one-byte country
index, and a three-byte GeoNames ID. Point order and the header boundaries
provide rank without storing it per point.

City names use `count + 1` three-byte offsets. An entry contains its display
name and may append GeoNames' lowercase search alias after a NUL byte. Canonical
names are case- and diacritic-folded transiently when the user searches; no
normalized copy is stored. Country names retain `u32` offsets.

## Country file

`countries.bin` uses the `CTRY` tag and format version 1. It stores a 360×180
one-degree raster. Whole-cell assignments avoid geometry work for country
interiors. Boundary cells contain polygon rings clipped to the cell and
quantized to its full `u16 × u16` range. A sparse 64-cell block directory
rejects ocean cells before reading geometry.

## Dispute file

`disputes.bin` uses the `DSPT` tag and format version 1. It stores stable
territory IDs, names, possible countries, Natural Earth worldview assignments,
and embedded country geometry. Ordinary and disputed geometry share the same
grid, so a lookup prepares the coordinate once. A neutral view preserves
ambiguity; a region view uses the source's region assignment when present, then
a matching claimant, then the source default.
