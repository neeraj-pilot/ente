import AuthenticationServices
import LocalAuthentication
import SwiftUI

final class CredentialProviderViewController: ASCredentialProviderViewController {
  private var model: CodePickerModel?
  private var unlockStarted = false

  override func prepareOneTimeCodeCredentialList(
    for serviceIdentifiers: [ASCredentialServiceIdentifier]
  ) {
    show(site: serviceIdentifiers.lazy.compactMap(Self.siteName).first, recordID: nil)
  }

  override func provideCredentialWithoutUserInteraction(
    for credentialRequest: any ASCredentialRequest
  ) {
    extensionContext.cancelRequest(withError: ASExtensionError(.userInteractionRequired))
  }

  override func prepareInterfaceToProvideCredential(for credentialRequest: any ASCredentialRequest)
  {
    let identity = credentialRequest.credentialIdentity
    show(site: Self.siteName(identity.serviceIdentifier), recordID: identity.recordIdentifier)
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    guard let model, !unlockStarted else { return }
    unlockStarted = true
    Task { await model.unlock() }
  }

  private func show(site: String?, recordID: String?) {
    let model = CodePickerModel(
      recordID: recordID,
      fill: { [weak self] code in
        self?.extensionContext.completeOneTimeCodeRequest(
          using: ASOneTimeCodeCredential(code: code))
      },
      cancel: { [weak self] in
        self?.extensionContext.cancelRequest(withError: ASExtensionError(.userCanceled))
      })
    self.model = model
    let host = UIHostingController(rootView: CodePicker(site: site, model: model))
    addChild(host)
    host.view.frame = view.bounds
    host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    view.addSubview(host.view)
    host.didMove(toParent: self)
  }

  private static func siteName(_ identifier: ASCredentialServiceIdentifier) -> String? {
    let host =
      identifier.type == .URL ? URL(string: identifier.identifier)?.host() : identifier.identifier
    var labels = host?.lowercased().split(separator: ".") ?? []
    if labels.count >= 3, labels.last?.count == 2,
      ["ac", "co", "com", "edu", "gov", "net", "org"].contains(labels[labels.count - 2])
    {
      labels.removeLast()
    }
    return labels.count >= 2 ? String(labels[labels.count - 2]) : nil
  }
}

@MainActor @Observable
private final class CodePickerModel {
  enum Phase {
    case waiting
    case failed(LocalizedStringKey, retry: Bool)
    case ready([Entry])
  }

  var phase = Phase.waiting
  let recordID: String?
  let fill: @MainActor (String) -> Void
  let cancel: @MainActor () -> Void

  init(
    recordID: String?, fill: @escaping @MainActor (String) -> Void,
    cancel: @escaping @MainActor () -> Void
  ) {
    self.recordID = recordID
    self.fill = fill
    self.cancel = cancel
  }

  func unlock() async {
    phase = .waiting
    let context = LAContext()
    guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else {
      phase = .failed("Set a passcode on this device to use AutoFill.", retry: false)
      return
    }
    do {
      try await context.evaluatePolicy(
        .deviceOwnerAuthentication, localizedReason: "Unlock to fill a code")
    } catch let error as LAError
      where [.userCancel, .appCancel, .systemCancel].contains(error.code)
    {
      return cancel()
    } catch {
      phase = .failed("Couldn't unlock.", retry: true)
      return
    }
    do {
      guard let snapshot = try AutoFillVault.read(), !snapshot.entries.isEmpty else {
        ASCredentialIdentityStore.shared.removeAllCredentialIdentities { _, _ in }
        phase = .failed("Open Ente Auth to make your codes available here.", retry: false)
        return
      }
      if let entry = snapshot.entries.first(where: { $0.id == recordID }),
        let code = try? entry.code(at: .now)
      {
        return fill(code)
      }
      phase = .ready(snapshot.entries)
    } catch {
      phase = .failed("Couldn't read your codes. Open Ente Auth and try again.", retry: false)
    }
  }
}

private struct CodePicker: View {
  let site: String?
  let model: CodePickerModel
  @State private var search = ""

  var body: some View {
    NavigationStack {
      content
        .navigationTitle("Ente Auth")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: model.cancel) }
        }
    }
  }

  @ViewBuilder private var content: some View {
    switch model.phase {
    case .waiting:
      ProgressView()
    case .failed(let message, let retry):
      VStack(spacing: 16) {
        Text(message).multilineTextAlignment(.center).foregroundStyle(.secondary)
        if retry {
          Button("Try again") { Task { await model.unlock() } }
        }
      }
      .padding()
    case .ready(let entries):
      list(entries)
    }
  }

  private func list(_ entries: [Entry]) -> some View {
    let query = search.trimmingCharacters(in: .whitespaces)
    let shown = entries.filter {
      query.isEmpty || $0.issuer.localizedCaseInsensitiveContains(query)
        || $0.account.localizedCaseInsensitiveContains(query)
    }
    let suggested = shown.filter(matchesSite)
    let others = shown.filter { !matchesSite($0) }
    return List {
      if suggested.isEmpty {
        ForEach(shown, content: row)
      } else {
        Section("Suggested") { ForEach(suggested, content: row) }
        if !others.isEmpty {
          Section("All codes") { ForEach(others, content: row) }
        }
      }
    }
    .searchable(text: $search)
  }

  private func row(_ entry: Entry) -> some View {
    Button {
      if let code = try? entry.code(at: .now) { model.fill(code) }
    } label: {
      VStack(alignment: .leading, spacing: 2) {
        Text(entry.issuer.isEmpty ? entry.account : entry.issuer)
        if !entry.issuer.isEmpty && !entry.account.isEmpty {
          Text(entry.account).font(.footnote).foregroundStyle(.secondary)
        }
      }
    }
    .foregroundStyle(.primary)
  }

  private func matchesSite(_ entry: Entry) -> Bool {
    guard let site = site?.filter({ $0.isLetter || $0.isNumber }) else { return false }
    let issuer = entry.issuer.lowercased().filter { $0.isLetter || $0.isNumber }
    if issuer == site { return true }
    return site.count >= 3 && issuer.count >= 3 && (issuer.contains(site) || site.contains(issuer))
  }
}
