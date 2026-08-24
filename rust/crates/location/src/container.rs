use std::ops::Range;

use crate::Error;
use crate::binary::{range, u16_at, u32_at};

const MAGIC: &[u8; 4] = b"ELI1";
const VERSION: u16 = 1;
const HEADER_LEN: usize = 64;
const SECTION: &str = "location asset";

pub(crate) struct Sections {
    pub version: u32,
    pub cities: Box<[u8]>,
    pub countries: Box<[u8]>,
    pub disputes: Box<[u8]>,
}

pub(crate) fn split(bytes: &[u8]) -> crate::Result<Sections> {
    if bytes.len() < HEADER_LEN {
        return Err(invalid("truncated"));
    }
    if &bytes[..4] != MAGIC {
        return Err(invalid("invalid magic"));
    }
    let format_version = u16_at(bytes, 4).ok_or(invalid("truncated"))?;
    if format_version != VERSION {
        return Err(Error::invalid(
            SECTION,
            format!("unsupported format version {format_version}"),
        ));
    }
    if usize::from(u16_at(bytes, 6).ok_or(invalid("truncated"))?) != HEADER_LEN {
        return Err(invalid("unexpected header length"));
    }
    if bytes[40..HEADER_LEN].iter().any(|&byte| byte != 0) {
        return Err(invalid("nonzero reserved header bytes"));
    }

    let cities = section(bytes, 12, 16)?;
    let countries = section(bytes, 20, 24)?;
    let disputes = section(bytes, 28, 32)?;
    let declared_length = read_usize(bytes, 36)?;
    if cities.start != HEADER_LEN
        || countries.start != cities.end
        || disputes.start != countries.end
        || disputes.end != bytes.len()
        || declared_length != bytes.len()
    {
        return Err(invalid("sections are not contiguous"));
    }

    Ok(Sections {
        version: u32_at(bytes, 8).ok_or(invalid("truncated"))?,
        cities: bytes[cities].into(),
        countries: bytes[countries].into(),
        disputes: bytes[disputes].into(),
    })
}

fn section(bytes: &[u8], offset_field: usize, length_field: usize) -> crate::Result<Range<usize>> {
    let offset = read_usize(bytes, offset_field)?;
    let length = read_usize(bytes, length_field)?;
    if length == 0 {
        return Err(invalid("empty section"));
    }
    let section = range(offset, length, 1).ok_or(invalid("section range overflow"))?;
    if section.end > bytes.len() {
        return Err(invalid("section exceeds file"));
    }
    Ok(section)
}

fn read_usize(bytes: &[u8], offset: usize) -> crate::Result<usize> {
    Ok(u32_at(bytes, offset).ok_or(invalid("truncated"))? as usize)
}

fn invalid(reason: &'static str) -> Error {
    Error::invalid(SECTION, reason)
}
