import CryptoKit
import Foundation

public struct Envelope: Codable, Sendable {
  public static let maximumBytes = 48 * 1024
  static let context = Data("io.ente.auth.watch.snapshot.v1".utf8)

  struct Header: Codable, Sendable {
    enum Kind: String, Codable, Sendable { case snapshot, retire }

    let version: Int
    let enrollment: String
    let suite: CipherSuite
    let revision: Int64
    let kind: Kind

    func validate() throws {
      guard revision > 0, enrollment.count == 64 else { throw WatchError.invalidEnvelope }
    }
  }

  private struct Body: Codable {
    let header: Data
    let encapsulatedKey: Data
    let ciphertext: Data
  }

  let header: Header
  let encapsulatedKey: Data
  let ciphertext: Data
  let data: Data
  private let authenticatedHeader: Data

  var digest: Data { Data(SHA256.hash(data: data)) }

  // Preserve sender bytes for AAD, storage, retries and receipts across app versions.
  static func decode(_ data: Data) throws -> Envelope {
    guard data.count <= maximumBytes else { throw WatchError.payloadTooLarge }
    let body = try JSONDecoder().decode(Body.self, from: data)
    struct Version: Decodable { let version: Int }
    guard try JSONDecoder().decode(Version.self, from: body.header).version == 1 else {
      throw WatchError.unsupportedVersion
    }
    let header = try JSONDecoder().decode(Header.self, from: body.header)
    try header.validate()
    return Envelope(
      header: header, encapsulatedKey: body.encapsulatedKey, ciphertext: body.ciphertext,
      data: data, authenticatedHeader: body.header
    )
  }

  private init(
    header: Header, encapsulatedKey: Data, ciphertext: Data, data: Data, authenticatedHeader: Data
  ) {
    self.header = header
    self.encapsulatedKey = encapsulatedKey
    self.ciphertext = ciphertext
    self.data = data
    self.authenticatedHeader = authenticatedHeader
  }

  public init(from decoder: any Decoder) throws {
    self = try Self.decode(decoder.singleValueContainer().decode(Data.self))
  }

  public func encode(to encoder: any Encoder) throws {
    var container = encoder.singleValueContainer()
    try container.encode(data)
  }

  @available(iOS 17, *)
  static func seal(
    _ snapshot: Snapshot, for enrollment: Enrollment, revision: Int64,
    kind: Header.Kind = .snapshot
  ) throws -> Envelope {
    try snapshot.validate()
    guard kind != .retire || snapshot.entries.isEmpty else { throw WatchError.invalidSnapshot }
    let header = Header(
      version: 1, enrollment: enrollment.id, suite: enrollment.suite, revision: revision, kind: kind
    )
    try header.validate()
    let headerData = try jsonData(header)
    var sender = try enrollment.sender(info: context)
    let ciphertext = try sender.seal(jsonData(snapshot), authenticating: headerData)
    return try decode(
      jsonData(
        Body(header: headerData, encapsulatedKey: sender.encapsulatedKey, ciphertext: ciphertext)))
  }

  @available(iOS 17, *)
  func open(using privateKey: PrivateKey) throws -> Snapshot {
    var recipient = try privateKey.recipient(for: self)
    let plaintext = try recipient.open(ciphertext, authenticating: authenticatedHeader)
    let snapshot = try JSONDecoder().decode(Snapshot.self, from: plaintext)
    try snapshot.validate()
    guard header.kind != .retire || snapshot.entries.isEmpty else {
      throw WatchError.invalidSnapshot
    }
    return snapshot
  }

  static func jsonData(_ value: some Encodable) throws -> Data {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys]
    return try encoder.encode(value)
  }
}

struct Receipt: Codable, Equatable, Sendable {
  let enrollment: String
  let revision: Int64
  let digest: Data

  init(applied envelope: Envelope) {
    enrollment = envelope.header.enrollment
    revision = envelope.header.revision
    digest = envelope.digest
  }

  // Call after HPKE open on the paired OS channel. Persist before sending a receipt.
  func needsReplacement(with envelope: Envelope) throws -> Bool {
    guard enrollment == envelope.header.enrollment else { throw WatchError.wrongEnrollment }
    if envelope.header.revision < revision { return false }
    if envelope.header.revision == revision {
      guard digest == envelope.digest else { throw WatchError.conflictingRevision }
      return false
    }
    return true
  }
}
