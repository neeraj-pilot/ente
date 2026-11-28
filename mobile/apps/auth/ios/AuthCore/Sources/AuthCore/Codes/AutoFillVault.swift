#if os(iOS)
  import Foundation
  import Security

  public enum AutoFillVault {
    private static var query: [String: Any] {
      [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "io.ente.auth.autofill",
        kSecAttrAccount as String: "snapshot",
        kSecAttrAccessGroup as String: Bundle.main.object(
          forInfoDictionaryKey: "EnteAutoFillKeychainGroup") as! String,
      ]
    }

    public static func read() throws -> Snapshot? {
      var request = query
      request[kSecReturnData as String] = true
      request[kSecMatchLimit as String] = kSecMatchLimitOne
      var result: CFTypeRef?
      let status = SecItemCopyMatching(request as CFDictionary, &result)
      if status == errSecItemNotFound { return nil }
      guard status == errSecSuccess, let data = result as? Data else {
        throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
      }
      return try JSONDecoder().decode(Snapshot.self, from: data)
    }

    @discardableResult
    public static func write(_ data: Data) throws -> Snapshot {
      let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
      try snapshot.validate()
      let status = SecItemUpdate(
        query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
      if status == errSecSuccess { return snapshot }
      guard status == errSecItemNotFound else {
        throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
      }
      var request = query
      request[kSecAttrAccessible as String] = kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
      request[kSecValueData as String] = data
      let added = SecItemAdd(request as CFDictionary, nil)
      guard added == errSecSuccess else {
        throw NSError(domain: NSOSStatusErrorDomain, code: Int(added))
      }
      return snapshot
    }

    public static func remove() throws {
      let status = SecItemDelete(query as CFDictionary)
      guard status == errSecSuccess || status == errSecItemNotFound else {
        throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
      }
    }
  }
#endif
