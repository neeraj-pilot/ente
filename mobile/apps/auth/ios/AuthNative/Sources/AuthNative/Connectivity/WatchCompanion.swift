#if os(watchOS)
  import Combine
  import Foundation
  import Security
  import WatchConnectivity

  @MainActor
  public final class WatchCompanion: NSObject, ObservableObject, WCSessionDelegate {
    public enum Issue: Equatable { case passcodeRequired, unlockWatch, syncFailed, updatePhone }

    @Published public private(set) var entries: [Entry] = []
    @Published public private(set) var status = WatchStatus(enrollment: nil, receipt: nil)
    @Published public private(set) var issue: Issue?
    @Published public private(set) var canEnroll = false
    @Published public private(set) var updatedAt: Date?

    private let session: WCSession
    private let repository: WatchRepository
    private let file: SnapshotFile
    private var visible = false
    private var phone: PhoneContext?

    public override init() {
      session = .default
      let directory = FileManager.default.urls(
        for: .applicationSupportDirectory, in: .userDomainMask)[0]
      file = SnapshotFile(url: directory.appendingPathComponent("watch/snapshot"))
      repository = WatchRepository(file: file)
      super.init()
      session.delegate = self
      session.activate()
    }

    public func setVisible(_ visible: Bool) {
      self.visible = visible
      if visible { reload() } else { entries = [] }
    }

    public func enroll() {
      guard visible,
        let suite = CipherSuite.supported.first(where: { phone?.suites.contains($0) == true })
      else {
        issue = .updatePhone
        return
      }
      do {
        _ = try repository.begin(suite: suite)
        issue = nil
        reload()
      } catch is KeychainError { issue = .passcodeRequired } catch { issue = .syncFailed }
    }

    public func erase() {
      entries = []
      do {
        try repository.erase()
        status = WatchStatus(enrollment: nil, receipt: nil)
        updatedAt = nil
        issue = nil
        try sendStatus()
      } catch { issue = .syncFailed }
    }

    private func reload() {
      guard visible else { return }
      do {
        var state = try repository.read()
        var syncFailed = false
        do {
          if !session.receivedApplicationContext.isEmpty {
            phone = try PhoneContext(session.receivedApplicationContext)
          }
          if let data = phone?.envelope { state = try repository.apply(data) }
        } catch WatchError.wrongEnrollment {
          // A delayed context can name a retired key.
        } catch {
          // Bad incoming data must not hide the last valid local snapshot.
          syncFailed = true
        }
        canEnroll = CipherSuite.supported.contains { phone?.suites.contains($0) == true }
        entries = state.snapshot.entries
        status = WatchStatus(enrollment: state.enrollment, receipt: state.receipt)
        updatedAt =
          state.receipt == nil
          ? nil
          : (try? file.url.resourceValues(forKeys: [.contentModificationDateKey]))?
            .contentModificationDate
        do { try sendStatus() } catch { syncFailed = true }
        issue = syncFailed ? .syncFailed : nil
      } catch is KeychainError {
        entries = []
        issue = .unlockWatch
      } catch {
        entries = []
        issue = .syncFailed
        status = WatchStatus(
          enrollment: try? repository.enrollment(), receipt: nil, storageError: true)
        try? sendStatus()
      }
    }

    private func sendStatus() throws {
      guard session.activationState == .activated else { return }
      let context = try status.context()
      if !NSDictionary(dictionary: session.applicationContext).isEqual(to: context) {
        try session.updateApplicationContext(context)
      }
    }

    nonisolated public func session(
      _ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
      error: (any Error)?
    ) {
      Task { @MainActor [weak self] in self?.reload() }
    }

    nonisolated public func session(
      _ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]
    ) {
      Task { @MainActor [weak self] in self?.reload() }
    }
  }
#endif
