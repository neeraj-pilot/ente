import AuthNative
import SwiftUI

@main
struct AuthWatchApp: App {
  @StateObject private var companion = WatchCompanion()

  var body: some Scene {
    WindowGroup { WatchHome(companion: companion) }
  }
}

private enum WatchStyle {
  static let accent = Color(red: 150 / 255, green: 16 / 255, blue: 214 / 255)
  static let warning = Color(red: 246 / 255, green: 58 / 255, blue: 58 / 255)
}

private struct WatchHome: View {
  @ObservedObject var companion: WatchCompanion
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.isLuminanceReduced) private var dimmed

  private var visible: Bool { scenePhase == .active && !dimmed }

  var body: some View {
    NavigationStack {
      List {
        if !visible {
          hiddenCodes
        } else {
          if let issue = companion.issue {
            Text(message(for: issue)).foregroundStyle(.secondary)
            if issue == .syncFailed {
              Text("If this continues, erase watch data in Manage and set up this watch again.")
                .foregroundStyle(.secondary)
            }
          }
          if companion.status.enrollment == nil {
            Text("Keep selected codes on this watch. A watch passcode is required.")
            Button("Set up watch", action: companion.enroll)
              .tint(WatchStyle.accent)
              .disabled(!companion.canEnroll)
            if !companion.canEnroll {
              Text("Open Auth on your iPhone to start.").foregroundStyle(.secondary)
            }
          } else if !companion.status.active {
            Text("On your iPhone, open Ente Auth → Settings → General → Watch to approve.")
          } else if companion.entries.isEmpty {
            Text("Select accounts in Auth on your iPhone.")
          }
          ForEach(companion.entries) { entry in
            NavigationLink(value: entry.id) {
              VStack(alignment: .leading, spacing: 4) {
                Text(entry.issuer.isEmpty ? entry.account : entry.issuer)
                  .font(.headline).lineLimit(1)
                if !entry.issuer.isEmpty && !entry.account.isEmpty {
                  Text(entry.account).font(.caption).foregroundStyle(.secondary)
                    .lineLimit(1).truncationMode(.middle)
                }
              }
            }
            .accessibilityLabel(
              [entry.issuer, entry.account].filter { !$0.isEmpty }.joined(separator: ", "))
          }
          if let updated = companion.updatedAt {
            TimelineView(.periodic(from: .now, by: 60)) { timeline in
              Text(updatedLabel(updated, now: timeline.date))
                .font(.caption).foregroundStyle(.secondary)
            }
            .listRowBackground(Color.clear)
          }
          if companion.status.enrollment != nil || companion.issue == .syncFailed {
            NavigationLink {
              if visible { WatchManagement(companion: companion) } else { hiddenCodes }
            } label: {
              Text("Manage")
            }
          }
        }
      }
      .navigationTitle("Ente Auth")
      .navigationDestination(for: String.self) { id in
        if visible, let entry = companion.entries.first(where: { $0.id == id }) {
          CodeDetail(entry: entry)
        } else {
          hiddenCodes
        }
      }
    }
    .privacySensitive()
    .onAppear { companion.setVisible(visible) }
    .onChange(of: visible) { _, value in companion.setVisible(value) }
  }

  private var hiddenCodes: some View {
    Image(systemName: "lock.shield").font(.largeTitle).accessibilityLabel("Codes hidden")
  }

  private func updatedLabel(_ date: Date, now: Date) -> LocalizedStringKey {
    if now.timeIntervalSince(date) < 60 { return "Updated just now" }
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .abbreviated
    return "Updated \(formatter.localizedString(for: date, relativeTo: now))"
  }

  private func message(for issue: WatchCompanion.Issue) -> LocalizedStringKey {
    switch issue {
    case .passcodeRequired: return "Set a passcode in watch Settings, then try again."
    case .unlockWatch: return "Unlock your watch to show codes."
    case .syncFailed: return "Could not sync. Open Auth on your iPhone and try again."
    case .updatePhone: return "Open Auth on a supported iPhone to set up this watch."
    }
  }
}

private struct WatchManagement: View {
  @ObservedObject var companion: WatchCompanion
  @Environment(\.dismiss) private var dismiss
  @State private var confirmErase = false

  var body: some View {
    List {
      Text("Open Auth on your iPhone to sync changes.").foregroundStyle(.secondary)
      Button("Erase watch data", role: .destructive) { confirmErase = true }
    }
    .navigationTitle("Manage")
    .confirmationDialog(
      "Erase all codes from this watch?", isPresented: $confirmErase, titleVisibility: .visible
    ) {
      Button("Erase watch data", role: .destructive) {
        companion.erase()
        dismiss()
      }
    } message: {
      Text("Your phone keeps its accounts. You must approve this watch again to sync codes.")
    }
  }
}

private struct CodeDetail: View {
  let entry: Entry

  var body: some View {
    ScrollView {
      TimelineView(
        .periodic(from: Date(timeIntervalSince1970: floor(Date().timeIntervalSince1970)), by: 1)
      ) { timeline in
        let remaining = entry.period - Int(timeline.date.timeIntervalSince1970) % entry.period
        let code = (try? entry.code(at: timeline.date)) ?? "—"
        VStack(spacing: 12) {
          if !entry.account.isEmpty {
            Text(entry.account).font(.caption).lineLimit(1).truncationMode(.middle)
              .foregroundStyle(.secondary)
          }
          Text(formatCode(code))
            .font(.system(.title2, design: .monospaced).bold())
            .minimumScaleFactor(0.6).lineLimit(1)
            .environment(\.layoutDirection, .leftToRight)
            .privacySensitive()
            .accessibilityLabel("Code")
            .accessibilityValue(code.map(String.init).joined(separator: " "))
          ProgressView(value: Double(remaining), total: Double(entry.period))
            .tint(remaining <= 5 ? WatchStyle.warning : WatchStyle.accent)
            .accessibilityHidden(true)
          Text(remaining == 1 ? "1 second" : "\(remaining) seconds")
            .font(.caption).foregroundStyle(.secondary)
        }
        .padding(.horizontal)
        .padding(.bottom, 16)
      }
      VStack(alignment: .leading, spacing: 4) {
        if !entry.issuer.isEmpty { Text(entry.issuer).font(.caption).bold() }
        if !entry.account.isEmpty {
          Text(entry.account).font(.caption).foregroundStyle(.secondary)
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .accessibilityHidden(true)
      .padding(.horizontal)
      .padding(.bottom)
    }
    .navigationTitle(entry.issuer.isEmpty ? "Code" : entry.issuer)
    .privacySensitive()
  }
}
