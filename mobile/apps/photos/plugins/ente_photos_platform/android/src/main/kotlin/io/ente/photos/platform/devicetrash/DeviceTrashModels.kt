package io.ente.photos.platform.devicetrash

sealed interface DeviceTrashResult {
    data object Unsupported : DeviceTrashResult

    data class Success(val files: List<TrashedMedia>) : DeviceTrashResult
}

data class TrashedMedia(
    val mediaStoreId: Long,
    val volumeName: String,
    val expiresAtEpochSeconds: Long,
    val bucketName: String?,
)
