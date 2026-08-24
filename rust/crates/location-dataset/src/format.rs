use sha2::{Digest, Sha256};

use crate::{Result, hex, invalid};

const HEADER_LEN: usize = 64;

pub(crate) fn encode(
    dataset_version: u32,
    cities: &[u8],
    countries: &[u8],
    disputes: &[u8],
) -> Result<Vec<u8>> {
    if cities.is_empty() || countries.is_empty() || disputes.is_empty() {
        return Err(invalid("location asset sections must be nonempty"));
    }
    let cities_offset = HEADER_LEN;
    let countries_offset = cities_offset
        .checked_add(cities.len())
        .ok_or_else(|| invalid("location asset size overflow"))?;
    let disputes_offset = countries_offset
        .checked_add(countries.len())
        .ok_or_else(|| invalid("location asset size overflow"))?;
    let file_length = disputes_offset
        .checked_add(disputes.len())
        .ok_or_else(|| invalid("location asset size overflow"))?;
    let mut output = vec![0; HEADER_LEN];
    output[..4].copy_from_slice(b"ELI1");
    put_u16(&mut output, 4, 1);
    put_u16(&mut output, 6, HEADER_LEN as u16);
    put_u32(&mut output, 8, dataset_version);
    for (field, value) in [
        (12, cities_offset),
        (16, cities.len()),
        (20, countries_offset),
        (24, countries.len()),
        (28, disputes_offset),
        (32, disputes.len()),
        (36, file_length),
    ] {
        put_length(&mut output, field, value)?;
    }
    output.extend_from_slice(cities);
    output.extend_from_slice(countries);
    output.extend_from_slice(disputes);
    Ok(output)
}

pub(crate) fn sha256(bytes: &[u8]) -> String {
    hex(Sha256::digest(bytes))
}

fn put_u16(output: &mut [u8], offset: usize, value: u16) {
    output[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
}

fn put_u32(output: &mut [u8], offset: usize, value: u32) {
    output[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn put_length(output: &mut [u8], offset: usize, value: usize) -> Result<()> {
    put_u32(
        output,
        offset,
        u32::try_from(value).map_err(|_| invalid("location asset exceeds 4 GiB"))?,
    );
    Ok(())
}
