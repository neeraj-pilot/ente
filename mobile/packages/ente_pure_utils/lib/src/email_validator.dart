const _maxEmailLength = 254;
const _maxLocalPartLength = 64;
const _maxDomainLabelLength = 63;
const _apostrophe = 0x27;
const _dot = 0x2E;
const _hyphen = 0x2D;
const _lowercaseN = 0x6E;
const _lowercaseX = 0x78;
const _plus = 0x2B;
const _underscore = 0x5F;

/// Whether [email] uses Ente's supported public internet email syntax.
///
/// Quoted local parts, domain literals, comments, and non-ASCII input are not
/// supported. Internationalized domains must be converted to their ASCII form
/// before validation.
bool isValidEmail(String email) {
  if (email.isEmpty || email.length > _maxEmailLength) {
    return false;
  }

  final separatorIndex = email.indexOf('@');
  if (separatorIndex <= 0 ||
      separatorIndex > _maxLocalPartLength ||
      separatorIndex != email.lastIndexOf('@') ||
      separatorIndex == email.length - 1) {
    return false;
  }

  return _isValidLocalPart(email, separatorIndex) &&
      _isValidDomain(email, separatorIndex + 1);
}

bool _isValidLocalPart(String email, int end) {
  var previousWasDot = true;
  for (var index = 0; index < end; index++) {
    final codeUnit = email.codeUnitAt(index);
    if (codeUnit == _dot) {
      if (previousWasDot) {
        return false;
      }
      previousWasDot = true;
    } else {
      if (!_isLocalAtomCodeUnit(codeUnit)) {
        return false;
      }
      previousWasDot = false;
    }
  }
  return !previousWasDot;
}

bool _isValidDomain(String email, int start) {
  var labelStart = start;
  var hasDot = false;
  for (var index = start; index <= email.length; index++) {
    final atEnd = index == email.length;
    if (!atEnd) {
      final codeUnit = email.codeUnitAt(index);
      if (codeUnit != _dot) {
        if (!_isAsciiLetter(codeUnit) &&
            !_isAsciiDigit(codeUnit) &&
            codeUnit != _hyphen) {
          return false;
        }
        continue;
      }
    }

    final labelLength = index - labelStart;
    if (labelLength == 0 ||
        labelLength > _maxDomainLabelLength ||
        email.codeUnitAt(labelStart) == _hyphen ||
        email.codeUnitAt(index - 1) == _hyphen) {
      return false;
    }

    if (atEnd) {
      return hasDot && _isValidTopLevelDomain(email, labelStart, index);
    }
    hasDot = true;
    labelStart = index + 1;
  }
  return false;
}

bool _isValidTopLevelDomain(String email, int start, int end) {
  if (end - start < 2) {
    return false;
  }
  for (var index = start; index < end; index++) {
    if (!_isAsciiLetter(email.codeUnitAt(index))) {
      return end - start > 4 &&
          _equalsAsciiIgnoreCase(email.codeUnitAt(start), _lowercaseX) &&
          _equalsAsciiIgnoreCase(email.codeUnitAt(start + 1), _lowercaseN) &&
          email.codeUnitAt(start + 2) == _hyphen &&
          email.codeUnitAt(start + 3) == _hyphen &&
          _isAsciiLetterOrDigit(email.codeUnitAt(start + 4));
    }
  }
  return true;
}

bool _isLocalAtomCodeUnit(int codeUnit) {
  return _isAsciiLetter(codeUnit) ||
      _isAsciiDigit(codeUnit) ||
      codeUnit == _apostrophe ||
      codeUnit == _plus ||
      codeUnit == _hyphen ||
      codeUnit == _underscore;
}

bool _isAsciiLetter(int codeUnit) {
  return (codeUnit >= 0x41 && codeUnit <= 0x5A) ||
      (codeUnit >= 0x61 && codeUnit <= 0x7A);
}

bool _isAsciiDigit(int codeUnit) {
  return codeUnit >= 0x30 && codeUnit <= 0x39;
}

bool _isAsciiLetterOrDigit(int codeUnit) {
  return _isAsciiLetter(codeUnit) || _isAsciiDigit(codeUnit);
}

bool _equalsAsciiIgnoreCase(int codeUnit, int lowercaseCodeUnit) {
  return codeUnit == lowercaseCodeUnit || codeUnit == lowercaseCodeUnit - 0x20;
}
