import Foundation

// A reset applies to every watch, including watches that are inactive at logout.
public struct VaultEpoch {
  private let url: URL

  public init(url: URL) { self.url = url }

  public func current() throws -> String {
    do { return try String(contentsOf: url, encoding: .utf8) } catch CocoaError.fileReadNoSuchFile {
      return try advance()
    }
  }

  public func advance() throws -> String {
    let value = UUID().uuidString
    // Public reset metadata remains writable for an automatic logout while locked.
    try writeDeviceFile(
      Data(value.utf8), to: url, protection: .completeFileProtectionUntilFirstUserAuthentication)
    return value
  }
}
