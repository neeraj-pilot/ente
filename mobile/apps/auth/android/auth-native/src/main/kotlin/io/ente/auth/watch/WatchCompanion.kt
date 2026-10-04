package io.ente.auth.watch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.UserNotAuthenticatedException
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class WatchCompanion(context: Context) {
    enum class Message { connectPhone, hidden, setup, approvePhone, selectAccounts, offline, unlockWatch,
        storageFailed, secureStorageUnavailable, setupFailed, syncFailed, ready }
    data class State(val entries: List<Entry> = emptyList(), val message: Message = Message.connectPhone,
                     val canEnroll: Boolean = false, val canErase: Boolean = false, val updatedAt: Long? = null)
    private val directory = File(context.noBackupFilesDir, "watch")
    private val snapshotFile = File(directory, "snapshot")
    private val repository = WatchRepository(context, snapshotFile)
    private val statusFile = DeviceFile(File(directory, "status"))
    private val transport = DataLayer(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var visible = false
    private var suites = emptyList<CipherSuite>()
    var onChange: ((State) -> Unit)? = null

    fun setVisible(value: Boolean) {
        visible = value
        if (!value) onChange?.invoke(State(message = Message.hidden))
        enqueue { if (value) refresh() else transport.stop() }
    }

    fun enroll() = action {
        requireWatch(visible && suites.isNotEmpty(), "watchUnavailable")
        repository.begin(suites.first())
    }

    fun erase() = action {
        requireWatch(visible, "watchLocked")
        repository.erase()
        statusFile.remove()
    }

    fun retry() { enqueue { if (visible) refresh() } }

    private fun action(block: () -> Unit) {
        enqueue {
            try { block(); refresh() } catch (error: Exception) { failed(error, storageFailure = false) }
        }
    }

    private fun refresh() {
        if (!visible) return
        var local = try { repository.read() } catch (error: Exception) { failed(error, storageFailure = true); return }
        var connected = false
        var invalidIncoming = false
        suites = emptyList()
        try {
            transport.start { enqueue { if (visible) refresh() } }
            val items = transport.items()
            val nodes = transport.connectedNodes()
            val phone = items.filter { it.path == DataLayer.CAPABILITIES && it.node in nodes }.singleOrNull()
            if (phone != null) {
                val capabilities = json(phone.data, 1024)
                requireWatch(capabilities.number("version") == 1L, "unsupportedVersion")
                val offered = capabilities.getAsJsonArray("suites").map { it.asString }.toSet()
                suites = CipherSuite.supported.filter { it.wire in offered }
                connected = true
            }
            val id = local.enrollment?.id ?: local.receipt?.enrollment
            if (id != null) {
                // Only the exact enrolled private key can open this path's ciphertext.
                val incoming = items.filter { it.path == DataLayer.snapshot(id) }
                for (item in incoming) {
                    try { local = repository.apply(item.data) }
                    catch (error: WatchException) {
                        if (error.code != "wrongEnrollment") invalidIncoming = true
                    } catch (_: Exception) { invalidIncoming = true }
                }
            }
            val status = WatchStatus(local.enrollment, local.receipt)
            statusFile.write(status.encode())
            transport.put(DataLayer.STATUS, status.encode())
        } catch (_: Exception) {
            // Connectivity does not affect the last authenticated local snapshot.
        }
        val message = when {
            invalidIncoming -> Message.syncFailed
            local.enrollment == null && suites.isNotEmpty() -> Message.setup
            local.enrollment == null -> Message.connectPhone
            local.receipt == null -> Message.approvePhone
            !connected -> Message.offline
            local.snapshot.entries.isEmpty() -> Message.selectAccounts
            else -> Message.ready
        }
        emit(State(local.snapshot.entries, message, local.enrollment == null && suites.isNotEmpty(),
            local.enrollment != null || local.receipt != null,
            snapshotFile.lastModified().takeIf { it > 0 }))
    }

    private fun failed(error: Exception, storageFailure: Boolean) {
        val locked = generateSequence<Throwable>(error) { it.cause }.any { it is UserNotAuthenticatedException } ||
            (error as? WatchException)?.code == "watchLocked"
        if (!locked && storageFailure) {
            try {
                transport.start { enqueue { if (visible) refresh() } }
                val previous = statusFile.read()?.let(WatchStatus::decode)
                transport.put(DataLayer.STATUS, WatchStatus(previous?.enrollment, previous?.receipt, true).encode())
            } catch (_: Exception) { }
        }
        val message = when {
            locked -> Message.unlockWatch
            (error as? WatchException)?.code == "secureStorageUnavailable" -> Message.secureStorageUnavailable
            storageFailure -> Message.storageFailed
            else -> Message.setupFailed
        }
        emit(State(message = message, canErase = true))
    }

    private fun enqueue(operation: () -> Unit) {
        try { worker.execute(operation) } catch (_: RejectedExecutionException) {
            // DataClient may deliver an already queued callback after close.
        }
    }

    private fun emit(state: State) { main.post { if (visible) onChange?.invoke(state) } }

    fun close() {
        visible = false
        onChange = null
        worker.execute { transport.stop() }
        worker.shutdown()
    }
}
