import AuthCore
import AuthenticationServices
import Flutter

final class AutoFillPlugin: NSObject, FlutterPlugin {
  static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(
      name: "io.ente.auth/autofill", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(AutoFillPlugin(), channel: channel)
  }

  func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard #available(iOS 18, *) else {
      return result(call.method == "isEnabled" ? false : nil)
    }
    switch call.method {
    case "isEnabled":
      ASCredentialIdentityStore.shared.getState { state in
        DispatchQueue.main.async { result(state.isEnabled) }
      }
    case "publish":
      do {
        guard let data = (call.arguments as? FlutterStandardTypedData)?.data else {
          throw CodeError.invalidData
        }
        let snapshot = try AutoFillVault.write(data)
        ASCredentialIdentityStore.shared.replaceCredentialIdentities(
          snapshot.entries.compactMap(Self.identity)
        ) { _, error in
          DispatchQueue.main.async { result(error.map(Self.flutterError)) }
        }
      } catch {
        result(Self.flutterError(error))
      }
    case "clear":
      do {
        try AutoFillVault.remove()
        ASCredentialIdentityStore.shared.removeAllCredentialIdentities { _, _ in
          DispatchQueue.main.async { result(nil) }
        }
      } catch {
        result(Self.flutterError(error))
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  @available(iOS 18, *)
  private static func identity(_ entry: Entry) -> ASOneTimeCodeCredentialIdentity? {
    let host = entry.issuer.trimmingCharacters(in: .whitespaces).lowercased()
    let labels = host.split(separator: ".", omittingEmptySubsequences: false)
    guard labels.count >= 2,
      labels.allSatisfy({ !$0.isEmpty && $0.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" } }
      )
    else { return nil }
    return ASOneTimeCodeCredentialIdentity(
      serviceIdentifier: ASCredentialServiceIdentifier(identifier: host, type: .domain),
      label: entry.account.isEmpty ? entry.issuer : entry.account,
      recordIdentifier: entry.id)
  }

  private static func flutterError(_ error: Error) -> FlutterError {
    FlutterError(code: "autoFillUnavailable", message: "\(error)", details: nil)
  }
}
