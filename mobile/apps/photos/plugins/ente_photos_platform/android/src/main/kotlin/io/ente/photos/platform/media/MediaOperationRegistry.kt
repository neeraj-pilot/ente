package io.ente.photos.platform.media

class MediaOperationRegistry<State, Outcome>(private val capacity: Int) {
    private data class Entry<State, Outcome>(
        val state: State,
        val completion: (Outcome) -> Unit,
    )

    private val entries = mutableMapOf<String, Entry<State, Outcome>>()

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun register(
        operationId: String,
        state: State,
        completion: (Outcome) -> Unit,
    ): Boolean {
        if (entries.size == capacity || entries.containsKey(operationId)) return false
        entries[operationId] = Entry(state, completion)
        return true
    }

    @Synchronized
    fun contains(operationId: String): Boolean = entries.containsKey(operationId)

    @Synchronized
    fun state(operationId: String): State? = entries[operationId]?.state

    fun complete(operationId: String, outcome: Outcome): State? {
        val entry = synchronized(this) { entries.remove(operationId) } ?: return null
        entry.completion(outcome)
        return entry.state
    }

    @Synchronized
    fun removeAll(): List<State> {
        val states = entries.values.map { it.state }
        entries.clear()
        return states
    }
}
