import Foundation
import XCTest

@testable import AuthCore

private struct VectorFile: Decodable {
  struct OTPVector: Decodable {
    let name: String
    let generator: Entry.Generator
    let algorithm: OTP.Algorithm
    let digits: Int
    let period: Int
    let secretBase64: String
    let timestamp: TimeInterval
    let expected: String
  }

  struct FormatVector: Decodable {
    let input: String
    let expected: String
  }

  let otp: [OTPVector]
  let format: [FormatVector]
}

final class OTPTests: XCTestCase {
  func testSharedVectorsAndFormatting() throws {
    let fixtureURL = URL(fileURLWithPath: #filePath)
      .deletingLastPathComponent()  // AuthCoreTests
      .deletingLastPathComponent()  // Tests
      .deletingLastPathComponent()  // AuthCore
      .deletingLastPathComponent()  // ios
      .deletingLastPathComponent()  // auth
      .appendingPathComponent("test/fixtures/watch_otp_vectors.json")
    let data = try Data(contentsOf: fixtureURL)
    let vectors = try JSONDecoder().decode(VectorFile.self, from: data)

    for vector in vectors.otp {
      let secret = try XCTUnwrap(Data(base64Encoded: vector.secretBase64), vector.name)
      let entry = Entry(
        id: vector.name,
        issuer: vector.name,
        account: "alice@ente.io",
        secret: secret,
        generator: vector.generator,
        algorithm: vector.algorithm,
        digits: vector.digits,
        period: vector.period
      )
      let code = try entry.code(at: Date(timeIntervalSince1970: vector.timestamp))
      XCTAssertEqual(code, vector.expected, vector.name)
    }

    for vector in vectors.format {
      XCTAssertEqual(formatCode(vector.input), vector.expected)
    }
  }

  func testInvalidEntriesRejected() {
    let validSecret = Data("12345678901234567890".utf8)
    let invalid: [Entry] = [
      Entry(
        id: "", issuer: "A", account: "B", secret: validSecret, generator: .totp, algorithm: .sha1,
        digits: 6, period: 30),
      Entry(
        id: "1", issuer: "A", account: "B", secret: Data(), generator: .totp, algorithm: .sha1,
        digits: 6, period: 30),
      Entry(
        id: "2", issuer: "A", account: "B", secret: validSecret, generator: .totp, algorithm: .sha1,
        digits: 0, period: 30),
      Entry(
        id: "3", issuer: "A", account: "B", secret: validSecret, generator: .totp, algorithm: .sha1,
        digits: 11, period: 30),
      Entry(
        id: "4", issuer: "A", account: "B", secret: validSecret, generator: .totp, algorithm: .sha1,
        digits: 6, period: 0),
      Entry(
        id: "5", issuer: "Steam", account: "B", secret: validSecret, generator: .steam,
        algorithm: .sha256, digits: 5, period: 30),
      Entry(
        id: "6", issuer: "Steam", account: "B", secret: validSecret, generator: .steam,
        algorithm: .sha1, digits: 6, period: 30),
    ]
    for entry in invalid {
      XCTAssertThrowsError(try entry.validate(), entry.id)
    }
  }
}
