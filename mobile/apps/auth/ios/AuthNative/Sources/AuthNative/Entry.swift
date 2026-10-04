import CryptoKit
import Foundation

public enum WatchError: String, Error, Equatable, Sendable {
  case invalidEntry
  case invalidSnapshot
  case invalidEnvelope
  case unsupportedVersion
  case unsupportedSuite
  case wrongEnrollment
  case conflictingRevision
  case payloadTooLarge
  case phoneLocked
  case staleVault
}

public struct Entry: Codable, Identifiable, Sendable {
  public enum Generator: String, Codable, Sendable {
    case totp, steam
  }

  public enum Algorithm: String, Codable, Sendable {
    case sha1, sha256, sha512
  }

  public let id: String
  public let issuer: String
  public let account: String
  public let secret: Data
  public let generator: Generator
  public let algorithm: Algorithm
  public let digits: Int
  public let period: Int

  public func validate() throws {
    guard !id.isEmpty, !secret.isEmpty else { throw WatchError.invalidEntry }
    switch generator {
    case .totp:
      guard (1...10).contains(digits), (1...Int(Int32.max)).contains(period) else {
        throw WatchError.invalidEntry
      }
    case .steam:
      guard algorithm == .sha1, digits == 5, period == 30 else {
        throw WatchError.invalidEntry
      }
    }
  }

  public func code(at date: Date) throws -> String {
    try validate()
    let seconds = date.timeIntervalSince1970
    guard seconds.isFinite, seconds >= 0, seconds < Double(Int64.max) else {
      throw WatchError.invalidEntry
    }
    var counter = (UInt64(seconds.rounded(.down)) / UInt64(period)).bigEndian
    let message = withUnsafeBytes(of: &counter) { Data($0) }
    let key = SymmetricKey(data: secret)
    let digest: [UInt8]
    switch algorithm {
    case .sha1:
      digest = Array(HMAC<Insecure.SHA1>.authenticationCode(for: message, using: key))
    case .sha256:
      digest = Array(HMAC<SHA256>.authenticationCode(for: message, using: key))
    case .sha512:
      digest = Array(HMAC<SHA512>.authenticationCode(for: message, using: key))
    }
    let offset = Int(digest[digest.count - 1] & 0x0f)
    var value = digest[offset..<offset + 4].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
    value &= 0x7fff_ffff
    if generator == .steam {
      let alphabet = Array("23456789BCDFGHJKMNPQRTVWXY")
      return String(
        (0..<5).map { _ in
          let character = alphabet[Int(value % UInt32(alphabet.count))]
          value /= UInt32(alphabet.count)
          return character
        })
    }
    let modulus = (0..<digits).reduce(UInt64(1)) { result, _ in result * 10 }
    let code = String(UInt64(value) % modulus)
    return String(repeating: "0", count: digits - code.count) + code
  }
}

public struct Snapshot: Codable, Sendable {
  public let entries: [Entry]

  public func validate() throws {
    guard Set(entries.map(\.id)).count == entries.count else {
      throw WatchError.invalidSnapshot
    }
    for entry in entries { try entry.validate() }
  }
}
