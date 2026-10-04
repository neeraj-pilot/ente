import Foundation

@available(iOS 17, *)
public struct WatchRepository {
  public struct State {
    public let enrollment: Enrollment?
    public let snapshot: Snapshot
    public let receipt: Receipt?
  }

  private let keys = PasscodeKeychain()
  private let file: SnapshotFile

  public init(file: SnapshotFile) {
    self.file = file
  }

  public func enrollment() throws -> Enrollment? { try keys.read()?.enrollment }

  // Call from one owner. Each read checks Keychain again; locked is not missing.
  public func read() throws -> State {
    let key = try keys.read()
    let envelope = try file.read()
    guard let key else {
      if let envelope, envelope.header.kind == .retire {
        return State(
          enrollment: nil, snapshot: Snapshot(entries: []), receipt: Receipt(applied: envelope))
      }
      try file.remove()
      return State(enrollment: nil, snapshot: Snapshot(entries: []), receipt: nil)
    }
    guard let envelope else {
      return State(enrollment: try key.enrollment, snapshot: Snapshot(entries: []), receipt: nil)
    }
    let snapshot = try envelope.open(using: key)
    let receipt = Receipt(applied: envelope)
    if envelope.header.kind == .retire {
      try keys.remove()
      return State(enrollment: nil, snapshot: snapshot, receipt: receipt)
    }
    return State(enrollment: try key.enrollment, snapshot: snapshot, receipt: receipt)
  }

  public func begin(suite: CipherSuite) throws -> Enrollment {
    if let enrollment = try read().enrollment { return enrollment }
    let key = try PrivateKey.generate(suite: suite)
    // Remove the previous enrollment's ciphertext before persisting the new key.
    try file.remove()
    try keys.write(key)
    return try key.enrollment
  }

  public func apply(_ data: Data) throws -> State {
    let state = try read()
    let envelope = try Envelope.decode(data)
    guard let key = try keys.read() else {
      // A retirement receipt remains valid after its private key has been destroyed.
      if state.receipt == Receipt(applied: envelope) { return state }
      throw WatchError.wrongEnrollment
    }
    _ = try envelope.open(using: key)
    if let receipt = state.receipt, try !receipt.needsReplacement(with: envelope) { return state }
    try file.write(envelope)
    // read finishes retirement after the file is durable, including after a crash.
    return try read()
  }

  public func erase() throws {
    // A stale WatchConnectivity context cannot be opened after this point.
    try keys.remove()
    try file.remove()
  }
}
