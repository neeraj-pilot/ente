import CryptoKit
import Foundation

@available(iOS 17, *)
public struct PhoneRepository {
  public struct Record: Codable, Sendable {
    public let enrollment: Enrollment
    let epoch: String
    public let envelope: Envelope
    let desiredDigest: Data
    public var receipt: Receipt?

    public var delivered: Bool {
      Receipt(applied: envelope) == receipt
    }
  }

  private let url: URL
  private let epoch: String

  public init(url: URL, epoch: String) {
    self.url = url
    self.epoch = epoch
  }

  public func read() throws -> Record? {
    let data: Data
    do { data = try Data(contentsOf: url) } catch CocoaError.fileReadNoSuchFile { return nil }
    let record: Record
    do { record = try JSONDecoder().decode(Record.self, from: data) } catch {
      // Lost state cannot authorize secret export. The reported watch key is retired
      // by the owner before another enrollment can be approved.
      try remove()
      return nil
    }
    guard record.epoch != epoch, record.envelope.header.kind == .snapshot else { return record }
    let cleared = try emptyRecord(after: record)
    try write(cleared)
    return cleared
  }

  public func approve(_ enrollment: Enrollment, receipt: Receipt?) throws -> Record {
    try start(enrollment, receipt: receipt, kind: .snapshot)
  }

  public func retireOrphan(_ enrollment: Enrollment, receipt: Receipt) throws -> Record {
    guard try read() == nil else { throw WatchError.wrongEnrollment }
    return try start(enrollment, receipt: receipt, kind: .retire)
  }

  private func start(_ enrollment: Enrollment, receipt: Receipt?, kind: Envelope.Header.Kind) throws
    -> Record
  {
    let revision = try nextRevision(
      after: receipt?.enrollment == enrollment.id ? receipt?.revision ?? 0 : 0)
    let snapshot = Snapshot(entries: [])
    let record = try Record(
      enrollment: enrollment, epoch: epoch,
      envelope: Envelope.seal(snapshot, for: enrollment, revision: revision, kind: kind),
      desiredDigest: digest(snapshot), receipt: nil
    )
    try write(record)
    return record
  }

  public func publish(_ snapshot: Snapshot, retire: Bool = false) throws -> Record {
    guard let previous = try read(), previous.envelope.header.kind != .retire else {
      throw WatchError.wrongEnrollment
    }
    try snapshot.validate()
    let desiredDigest = try digest(snapshot)
    let receiptAhead = (previous.receipt?.revision ?? 0) > previous.envelope.header.revision
    let receiptConflict =
      previous.receipt?.revision == previous.envelope.header.revision && !previous.delivered
    if !retire, desiredDigest == previous.desiredDigest, !receiptAhead, !receiptConflict {
      return previous
    }
    let revision = try nextRevision(
      after: max(previous.envelope.header.revision, previous.receipt?.revision ?? 0))
    let record = try Record(
      enrollment: previous.enrollment, epoch: epoch,
      envelope: Envelope.seal(
        snapshot, for: previous.enrollment, revision: revision, kind: retire ? .retire : .snapshot),
      desiredDigest: desiredDigest, receipt: previous.receipt
    )
    try write(record)
    return record
  }

  public func acknowledge(_ receipt: Receipt) throws {
    guard var record = try read(), receipt.enrollment == record.enrollment.id else { return }
    guard receipt.revision > 0, receipt.digest.count == 32 else { throw WatchError.invalidEnvelope }
    guard receipt != record.receipt, receipt.revision >= (record.receipt?.revision ?? 0) else {
      return
    }
    record.receipt = receipt
    if !record.delivered, receipt.revision >= record.envelope.header.revision,
      record.desiredDigest == (try digest(Snapshot(entries: [])))
    {
      record = try emptyRecord(after: record)
    }
    try write(record)
  }

  public func remove() throws {
    do { try FileManager.default.removeItem(at: url) } catch CocoaError.fileNoSuchFile {}
  }

  private func emptyRecord(after previous: Record) throws -> Record {
    let snapshot = Snapshot(entries: [])
    let revision = try nextRevision(
      after: max(previous.envelope.header.revision, previous.receipt?.revision ?? 0))
    return try Record(
      enrollment: previous.enrollment, epoch: epoch,
      envelope: Envelope.seal(
        snapshot, for: previous.enrollment, revision: revision, kind: previous.envelope.header.kind),
      desiredDigest: digest(snapshot), receipt: previous.receipt
    )
  }

  private func digest(_ snapshot: Snapshot) throws -> Data {
    Data(SHA256.hash(data: try Envelope.jsonData(snapshot)))
  }

  private func nextRevision(after revision: Int64) throws -> Int64 {
    guard revision < Int64.max else { throw WatchError.invalidEnvelope }
    return revision + 1
  }

  private func write(_ record: Record) throws {
    try writeDeviceFile(try JSONEncoder().encode(record), to: url)
  }
}
