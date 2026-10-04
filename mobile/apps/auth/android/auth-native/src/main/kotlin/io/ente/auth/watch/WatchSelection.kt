package io.ente.auth.watch

internal data class WatchSelection(val request: WatchStatus?, val ambiguous: Boolean) {
    companion object {
        fun choose(record: PhoneRepository.Record?, connected: List<WatchStatus>,
                   known: List<WatchStatus> = connected): WatchSelection {
            if (record?.envelope?.retire == false) {
                connected.firstOrNull { it.enrollment?.id == record.enrollment.id }?.let {
                    return WatchSelection(it, false)
                }
            }
            val candidates = connected.filter { it.enrollment != null &&
                (record?.envelope?.retire != true || it.enrollment.id != record.enrollment.id) }
                .distinctBy { it.enrollment!!.id }
            if (candidates.isNotEmpty()) return WatchSelection(candidates.singleOrNull(), candidates.size > 1)
            val cached = if (record?.envelope?.retire == false)
                known.firstOrNull { it.enrollment?.id == record.enrollment.id } else null
            return WatchSelection(cached, false)
        }
    }
}
