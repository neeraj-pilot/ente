# Ente location index formats

All integers and floating-point values are little-endian. Strings are UTF-8.
Readers validate every count, offset, reference, coordinate, and string before
serving queries.

## ELI1 container

The 64-byte header contains the dataset version followed by offset/length pairs
for the `ELC2`, `ECB3`, and `EDB1` sections. The three sections are contiguous;
the remaining header bytes are reserved and must be zero.

| Offset | Type | Meaning |
| ---: | --- | --- |
| 0 | `[u8; 4]` | `ELI1` |
| 4 | `u16` | Format version (`1`) |
| 6 | `u16` | Header length (`64`) |
| 8 | `u32` | Dataset version |
| 12 | `u32` | City-section offset |
| 16 | `u32` | City-section length |
| 20 | `u32` | Country-section offset |
| 24 | `u32` | Country-section length |
| 28 | `u32` | Dispute-section offset |
| 32 | `u32` | Dispute-section length |
| 36 | `u32` | File length |

## ELC2 city index

`ELC2` stores priority-ordered city points and a balanced two-dimensional KD
tree. The 72-byte header contains point/name/country counts, section offsets,
record lengths, the file length, and the cumulative ends of ranks 4 through 1.

The tree is preorder, so each three-byte node needs only its point index. Child
positions and split axes follow from subtree size and depth. Each 15-byte point
contains `f32` latitude/longitude, a three-byte name index, a one-byte country
index, and a three-byte GeoNames ID. Point order and the header boundaries
provide rank without storing it per point.

City names use `count + 1` three-byte offsets. An entry contains its display
name and may append GeoNames' lowercase search alias after a NUL byte. Canonical
names are lowercased transiently when the user searches; no lowercase copy is
stored. Country names retain `u32` offsets.

## ECB3 country geometry

`ECB3` uses a 360×180 one-degree raster. Whole-cell assignments avoid geometry
work for country interiors. Boundary cells contain polygon rings clipped to the
cell and quantized to its full `u16 × u16` range. A sparse 64-cell block
directory rejects ocean cells before reading geometry.

## EDB1 disputed-territory overlay

`EDB1` stores stable territory IDs, names, source notes, possible countries,
Natural Earth worldview assignments, and an embedded `ECB3` geometry section.
Ordinary and disputed geometry share the same grid, so a classification prepares
the coordinate once. A neutral view preserves ambiguity; a region view uses the
source's region assignment when present, then a matching claimant, then the
source default.
