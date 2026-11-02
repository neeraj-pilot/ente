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

  public let id: String
  public let issuer: String
  public let account: String
  public let secret: Data
  public let generator: Generator
  public let algorithm: OTP.Algorithm
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
    let code =
      switch generator {
      case .totp:
        OTP.totp(secret: secret, algorithm: algorithm, digits: digits, period: period, at: date)
      case .steam:
        OTP.steam(secret: secret, at: date)
      }
    guard let code else { throw WatchError.invalidEntry }
    return code
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
