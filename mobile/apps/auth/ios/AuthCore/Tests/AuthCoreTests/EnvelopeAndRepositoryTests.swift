import Compression
import CryptoKit
import Foundation
import XCTest

@testable import AuthCore

final class EnvelopeAndRepositoryTests: XCTestCase {
  func testEnvelopeRoundTripCompressionAndTamperRejection() throws {
    let entries = (0..<300).map { idx in
      Entry(
        id: "entry-\(idx)",
        issuer: "Service \(idx)",
        account: "alice+\(idx)@example.com",
        secret: Data("12345678901234567890".utf8),
        generator: .totp,
        algorithm: .sha1,
        digits: 6,
        period: 30
      )
    }
    let snapshot = Snapshot(entries: entries)

    for suite in CipherSuite.supported {
      let key = try PrivateKey.generate(suite: suite)
      let enrollment = try key.enrollment
      let envelope = try Envelope.seal(snapshot, for: enrollment, revision: 1)

      XCTAssertEqual(envelope.header.encoding, "deflate")
      XCTAssertLessThan(envelope.data.count, Envelope.maximumBytes)

      let decoded = try Envelope.decode(envelope.data)
      let opened = try decoded.open(using: key)
      XCTAssertEqual(opened.entries.count, 300)
      XCTAssertEqual(opened.entries.first?.id, "entry-0")
      XCTAssertEqual(opened.entries.last?.id, "entry-299")

      let wrongKey = try PrivateKey.generate(suite: suite)
      XCTAssertThrowsError(try decoded.open(using: wrongKey))

      var mutated = try XCTUnwrap(
        JSONSerialization.jsonObject(with: envelope.data) as? [String: Any])
      if let ct = mutated["ciphertext"] as? String, var rawCt = Data(base64Encoded: ct) {
        rawCt[0] ^= 0xff
        mutated["ciphertext"] = rawCt.base64EncodedString()
        let tamperedData = try JSONSerialization.data(withJSONObject: mutated)
        let tampered = try Envelope.decode(tamperedData)
        XCTAssertThrowsError(try tampered.open(using: key))
      }
    }
  }

  func testDecompressionBombRejectedBeforeFullAllocation() throws {
    let key = try PrivateKey.generate(suite: .p256)
    let enrollment = try key.enrollment
    let huge = Data(repeating: 0x41, count: Envelope.maxPlaintextBytes + 1024)
    let compressed = try compressRawDeflate(huge)
    let header = Envelope.Header(
      version: 1, enrollment: enrollment.id, suite: .p256, revision: 1, kind: .snapshot,
      encoding: "deflate")
    let headerData = try Envelope.jsonData(header)
    var sender = try enrollment.sender(info: Envelope.context)
    let ciphertext = try sender.seal(compressed, authenticating: headerData)
    let body: [String: Data] = [
      "header": headerData,
      "encapsulatedKey": sender.encapsulatedKey,
      "ciphertext": ciphertext,
    ]
    let wire = try JSONEncoder().encode(body)
    let envelope = try Envelope.decode(wire)

    XCTAssertThrowsError(try envelope.open(using: key)) { error in
      XCTAssertEqual(error as? WatchError, .payloadTooLarge)
    }
  }

  func testReceiptAndPhoneRepositoryLifecycle() throws {
    let tempDir = FileManager.default.temporaryDirectory
      .appendingPathComponent("authcore-\(UUID().uuidString)")
    try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: tempDir) }

    let epoch = VaultEpoch(url: tempDir.appendingPathComponent("epoch"))
    let initialEpoch = try epoch.current()
    let repo = PhoneRepository(url: tempDir.appendingPathComponent("phone-state"), epoch: initialEpoch)

    let watchKey = try PrivateKey.generate(suite: .p256)
    let enrollment = try watchKey.enrollment
    let approved = try repo.approve(enrollment, receipt: nil)
    XCTAssertEqual(approved.envelope.header.revision, 1)
    XCTAssertFalse(approved.delivered)

    let ack1 = Receipt(applied: approved.envelope)
    try repo.acknowledge(ack1)
    XCTAssertTrue(try XCTUnwrap(repo.read()).delivered)

    let entry = Entry(
      id: "1", issuer: "GitHub", account: "alice", secret: Data("12345678901234567890".utf8),
      generator: .totp, algorithm: .sha1, digits: 6, period: 30)
    let published = try repo.publish(Snapshot(entries: [entry]))
    XCTAssertEqual(published.envelope.header.revision, 2)
    XCTAssertFalse(published.delivered)

    // Same desired digest should be a no-op.
    let deduplicated = try repo.publish(Snapshot(entries: [entry]))
    XCTAssertEqual(deduplicated.envelope.header.revision, 2)

    // Higher revision requires replacement; same revision with mismatched digest throws.
    XCTAssertTrue(try ack1.needsReplacement(with: published.envelope))
    XCTAssertFalse(try Receipt(applied: published.envelope).needsReplacement(with: published.envelope))
    let conflictingEnvelope = try Envelope.seal(
      Snapshot(entries: []), for: enrollment, revision: published.envelope.header.revision)
    let conflictingReceipt = Receipt(applied: conflictingEnvelope)
    XCTAssertThrowsError(try conflictingReceipt.needsReplacement(with: published.envelope)) { err in
      XCTAssertEqual(err as? WatchError, .conflictingRevision)
    }

    // Advancing epoch emits an empty replacement snapshot.
    let nextEpoch = try epoch.advance()
    let nextRepo = PhoneRepository(url: tempDir.appendingPathComponent("phone-state"), epoch: nextEpoch)
    let resetRecord = try XCTUnwrap(nextRepo.read())
    let resetSnapshot = try resetRecord.envelope.open(using: watchKey)
    XCTAssertTrue(resetSnapshot.entries.isEmpty)
    XCTAssertGreaterThan(resetRecord.envelope.header.revision, 2)

    // Orphan retirement creates a .retire envelope with empty entries.
    let orphanRepo = PhoneRepository(url: tempDir.appendingPathComponent("orphan"), epoch: nextEpoch)
    let orphan = try orphanRepo.retireOrphan(enrollment, receipt: ack1)
    XCTAssertEqual(orphan.envelope.header.kind, .retire)
    XCTAssertEqual(orphan.envelope.header.revision, 2)
  }

  func testSnapshotMetadataRoundTripAndBackwardCompatibility() throws {
    let taggedEntry = Entry(
      id: "42",
      issuer: "Ente",
      account: "alice@ente.io",
      secret: Data("12345678901234567890".utf8),
      generator: .totp,
      algorithm: .sha1,
      digits: 6,
      period: 30,
      pinned: true,
      tags: ["Daily", "Work"]
    )
    let snapshot = Snapshot(entries: [taggedEntry], hideCodes: true)
    let encoded = try Envelope.jsonData(snapshot)
    let decoded = try JSONDecoder().decode(Snapshot.self, from: encoded)
    XCTAssertTrue(decoded.hideCodes)
    XCTAssertEqual(decoded.entries.first?.pinned, true)
    XCTAssertEqual(decoded.entries.first?.tags, ["Daily", "Work"])

    let legacyJson = Data(
      """
      {"entries":[{"id":"7","issuer":"Legacy","account":"a@b.c","secret":"MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=","generator":"totp","algorithm":"sha1","digits":6,"period":30}]}
      """.utf8)
    let legacy = try JSONDecoder().decode(Snapshot.self, from: legacyJson)
    XCTAssertFalse(legacy.hideCodes)
    XCTAssertEqual(legacy.entries.first?.pinned, false)
    XCTAssertEqual(legacy.entries.first?.tags, [])
  }

  private func compressRawDeflate(_ data: Data) throws -> Data {
    let dummy = UnsafeMutablePointer<UInt8>.allocate(capacity: 1)
    defer { dummy.deallocate() }
    var stream = compression_stream(
      dst_ptr: dummy, dst_size: 0, src_ptr: UnsafePointer(dummy), src_size: 0, state: nil)
    XCTAssertNotEqual(
      compression_stream_init(&stream, COMPRESSION_STREAM_ENCODE, COMPRESSION_ZLIB),
      COMPRESSION_STATUS_ERROR)
    defer { compression_stream_destroy(&stream) }
    var out = Data()
    var buf = [UInt8](repeating: 0, count: 4096)
    data.withUnsafeBytes { src in
      stream.src_ptr = src.bindMemory(to: UInt8.self).baseAddress!
      stream.src_size = src.count
      while true {
        let status = buf.withUnsafeMutableBytes { dst in
          stream.dst_ptr = dst.bindMemory(to: UInt8.self).baseAddress!
          stream.dst_size = 4096
          return compression_stream_process(&stream, Int32(COMPRESSION_STREAM_FINALIZE.rawValue))
        }
        out.append(contentsOf: buf[0..<(4096 - stream.dst_size)])
        if status == COMPRESSION_STATUS_END { break }
      }
    }
    return out
  }
}
