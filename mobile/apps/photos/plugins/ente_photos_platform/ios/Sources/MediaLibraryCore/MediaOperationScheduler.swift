import Foundation

public final class MediaOperationScheduler {
    public enum Submission: Equatable {
        case started
        case queued
        case duplicate
        case full
    }

    public enum Cancellation: Equatable {
        case active
        case pending
        case missing
    }

    private struct PendingOperation {
        let id: String
        let start: () -> Void
    }

    private let maximumActive: Int
    private let maximumPending: Int
    private var active: Set<String> = []
    private var pending: [PendingOperation] = []

    public init(maximumActive: Int = 2, maximumPending: Int = 32) {
        precondition(maximumActive > 0 && maximumPending >= 0)
        self.maximumActive = maximumActive
        self.maximumPending = maximumPending
    }

    @discardableResult
    public func submit(operationID: String, start: @escaping () -> Void) -> Submission {
        guard !active.contains(operationID), !pending.contains(where: { $0.id == operationID }) else {
            return .duplicate
        }
        if active.count < maximumActive {
            active.insert(operationID)
            start()
            return .started
        }
        guard pending.count < maximumPending else { return .full }
        pending.append(PendingOperation(id: operationID, start: start))
        return .queued
    }

    @discardableResult
    public func finish(operationID: String) -> Bool {
        guard active.remove(operationID) != nil else { return false }
        startNext()
        return true
    }

    @discardableResult
    public func cancel(operationID: String) -> Cancellation {
        if active.contains(operationID) {
            return .active
        }
        guard let index = pending.firstIndex(where: { $0.id == operationID }) else {
            return .missing
        }
        pending.remove(at: index)
        return .pending
    }

    public func removeAll() {
        active.removeAll()
        pending.removeAll()
    }

    private func startNext() {
        guard active.count < maximumActive, !pending.isEmpty else { return }
        let operation = pending.removeLast()
        active.insert(operation.id)
        operation.start()
    }
}
