import CryptoKit
import Foundation

public enum CipherSuite: String, Codable, Sendable {
  case p256 = "p256-aes256gcm"
  case xwing = "xwing-aes256gcm"

  public static var supported: [CipherSuite] {
    if #available(iOS 26, watchOS 26, *) { return [.xwing, .p256] }
    if #available(iOS 17, watchOS 10, *) { return [.p256] }
    return []
  }

  @available(iOS 17, *)
  var hpke: HPKE.Ciphersuite {
    get throws {
      switch self {
      case .p256: return .P256_SHA256_AES_GCM_256
      case .xwing:
        guard #available(iOS 26, watchOS 26, *) else {
          throw WatchError.unsupportedSuite
        }
        return .XWingMLKEM768X25519_SHA256_AES_GCM_256
      }
    }
  }
}

public struct Enrollment: Codable, Equatable, Sendable {
  public let suite: CipherSuite
  public let publicKey: Data

  public var id: String {
    SHA256.hash(data: Data(suite.rawValue.utf8) + publicKey)
      .map { String(format: "%02x", $0) }.joined()
  }

  public init(suite: CipherSuite, publicKey: Data) {
    self.suite = suite
    self.publicKey = publicKey
  }

  @available(iOS 17, *)
  public func validate() throws {
    _ = try sender(info: Envelope.context)
  }

  @available(iOS 17, *)
  func sender(info: Data) throws -> HPKE.Sender {
    switch suite {
    case .p256:
      return try HPKE.Sender(
        recipientKey: P256.KeyAgreement.PublicKey(x963Representation: publicKey),
        ciphersuite: suite.hpke, info: info
      )
    case .xwing:
      guard #available(iOS 26, watchOS 26, *) else { throw WatchError.unsupportedSuite }
      return try HPKE.Sender(
        recipientKey: XWingMLKEM768X25519.PublicKey(rawRepresentation: publicKey),
        ciphersuite: suite.hpke, info: info
      )
    }
  }
}

// Only the watch persists this value, in its passcode-protected Keychain.
@available(iOS 17, *)
public struct PrivateKey: Codable, Sendable {
  public let suite: CipherSuite
  public let representation: Data

  public static func generate(suite: CipherSuite) throws -> PrivateKey {
    switch suite {
    case .p256:
      return PrivateKey(
        suite: suite, representation: P256.KeyAgreement.PrivateKey().rawRepresentation)
    case .xwing:
      guard #available(iOS 26, watchOS 26, *) else {
        throw WatchError.unsupportedSuite
      }
      return try PrivateKey(
        suite: suite,
        representation: XWingMLKEM768X25519.PrivateKey().integrityCheckedRepresentation
      )
    }
  }

  public var enrollment: Enrollment {
    get throws {
      let publicKey: Data
      switch suite {
      case .p256:
        publicKey = try P256.KeyAgreement.PrivateKey(rawRepresentation: representation)
          .publicKey.x963Representation
      case .xwing:
        guard #available(iOS 26, watchOS 26, *) else {
          throw WatchError.unsupportedSuite
        }
        publicKey = try XWingMLKEM768X25519.PrivateKey(
          integrityCheckedRepresentation: representation
        )
        .publicKey.rawRepresentation
      }
      return Enrollment(suite: suite, publicKey: publicKey)
    }
  }

  func recipient(for envelope: Envelope) throws -> HPKE.Recipient {
    guard envelope.header.enrollment == (try enrollment.id), envelope.header.suite == suite else {
      throw WatchError.wrongEnrollment
    }
    switch suite {
    case .p256:
      return try HPKE.Recipient(
        privateKey: P256.KeyAgreement.PrivateKey(rawRepresentation: representation),
        ciphersuite: suite.hpke, info: Envelope.context, encapsulatedKey: envelope.encapsulatedKey
      )
    case .xwing:
      guard #available(iOS 26, watchOS 26, *) else {
        throw WatchError.unsupportedSuite
      }
      return try HPKE.Recipient(
        privateKey: XWingMLKEM768X25519.PrivateKey(integrityCheckedRepresentation: representation),
        ciphersuite: suite.hpke, info: Envelope.context, encapsulatedKey: envelope.encapsulatedKey
      )
    }
  }
}
