import Foundation

// Only ciphertext is stored here. The private key has a separate protection boundary.
public struct SnapshotFile: Sendable {
  public let url: URL

  public init(url: URL) { self.url = url }

  public func read() throws -> Envelope? {
    do {
      return try Envelope.decode(Data(contentsOf: url))
    } catch CocoaError.fileReadNoSuchFile {
      return nil
    }
  }

  public func write(_ envelope: Envelope) throws {
    try writeDeviceFile(envelope.data, to: url)
  }

  public func remove() throws {
    do { try FileManager.default.removeItem(at: url) } catch CocoaError.fileNoSuchFile {}
  }
}

func writeDeviceFile(
  _ data: Data, to url: URL, protection: Data.WritingOptions = .completeFileProtection
) throws {
  try FileManager.default.createDirectory(
    at: url.deletingLastPathComponent(), withIntermediateDirectories: true
  )
  try data.write(to: url, options: protection.union(.atomic))
  var file = url
  var values = URLResourceValues()
  values.isExcludedFromBackup = true
  try file.setResourceValues(values)
}
