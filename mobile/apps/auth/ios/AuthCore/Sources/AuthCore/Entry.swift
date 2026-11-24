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

public struct Entry: Codable, Identifiable, Sendable, Equatable {
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
  public let pinned: Bool
  public let tags: [String]

  public init(
    id: String, issuer: String, account: String, secret: Data, generator: Generator,
    algorithm: OTP.Algorithm, digits: Int, period: Int, pinned: Bool = false, tags: [String] = []
  ) {
    self.id = id
    self.issuer = issuer
    self.account = account
    self.secret = secret
    self.generator = generator
    self.algorithm = algorithm
    self.digits = digits
    self.period = period
    self.pinned = pinned
    self.tags = tags
  }

  private enum CodingKeys: String, CodingKey {
    case id, issuer, account, secret, generator, algorithm, digits, period, pinned, tags
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decode(String.self, forKey: .id)
    issuer = try container.decode(String.self, forKey: .issuer)
    account = try container.decode(String.self, forKey: .account)
    secret = try container.decode(Data.self, forKey: .secret)
    generator = try container.decode(Generator.self, forKey: .generator)
    algorithm = try container.decode(OTP.Algorithm.self, forKey: .algorithm)
    digits = try container.decode(Int.self, forKey: .digits)
    period = try container.decode(Int.self, forKey: .period)
    pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
    tags = try container.decodeIfPresent([String].self, forKey: .tags) ?? []
  }

  public func encode(to encoder: any Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(id, forKey: .id)
    try container.encode(issuer, forKey: .issuer)
    try container.encode(account, forKey: .account)
    try container.encode(secret, forKey: .secret)
    try container.encode(generator, forKey: .generator)
    try container.encode(algorithm, forKey: .algorithm)
    try container.encode(digits, forKey: .digits)
    try container.encode(period, forKey: .period)
    if pinned { try container.encode(true, forKey: .pinned) }
    if !tags.isEmpty { try container.encode(tags, forKey: .tags) }
  }

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

public struct Snapshot: Codable, Sendable, Equatable {
  public let entries: [Entry]
  public let hideCodes: Bool

  public init(entries: [Entry], hideCodes: Bool = false) {
    self.entries = entries
    self.hideCodes = hideCodes
  }

  private enum CodingKeys: String, CodingKey {
    case entries, hideCodes
  }

  public init(from decoder: any Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    entries = try container.decode([Entry].self, forKey: .entries)
    hideCodes = try container.decodeIfPresent(Bool.self, forKey: .hideCodes) ?? false
  }

  public func encode(to encoder: any Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(entries, forKey: .entries)
    if hideCodes { try container.encode(true, forKey: .hideCodes) }
  }

  public func validate() throws {
    guard Set(entries.map(\.id)).count == entries.count else {
      throw WatchError.invalidSnapshot
    }
    for entry in entries { try entry.validate() }
  }
}
