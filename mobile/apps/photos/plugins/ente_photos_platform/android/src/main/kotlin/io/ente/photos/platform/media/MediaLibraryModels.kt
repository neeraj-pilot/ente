package io.ente.photos.platform.media

import java.nio.charset.StandardCharsets

enum class MediaAssetKind {
    IMAGE,
    VIDEO,
}

enum class MediaThumbnailFit {
    CONTAIN,
    COVER,
}

data class MediaThumbnailRequest(
    val assetId: String,
    val kind: MediaAssetKind,
    val widthPx: Int,
    val heightPx: Int,
    val fit: MediaThumbnailFit,
    val quality: Int,
    val allowNetworkAccess: Boolean,
) {
    init {
        require(assetId.isNotEmpty() && assetId.toByteArray(StandardCharsets.UTF_8).size <= 1024)
        require(widthPx in 1..2048)
        require(heightPx in 1..2048)
        require(widthPx.toLong() * heightPx <= 4_000_000)
        require(quality in 1..100)
    }
}

data class MediaThumbnail(
    val jpegBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(
            jpegBytes.size >= 4 &&
                jpegBytes[0] == 0xff.toByte() &&
                jpegBytes[1] == 0xd8.toByte() &&
                jpegBytes[jpegBytes.lastIndex - 1] == 0xff.toByte() &&
                jpegBytes[jpegBytes.lastIndex] == 0xd9.toByte(),
        )
        require(widthPx in 1..2048 && heightPx in 1..2048)
        require(widthPx.toLong() * heightPx <= 4_000_000)
    }
}

enum class MediaLibraryError {
    CANCELLED,
    PERMISSION_DENIED,
    ASSET_NOT_FOUND,
    RESOURCE_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    INVALID_REQUEST,
    BUSY,
    UNSUPPORTED_PLATFORM,
    PLATFORM_FAILURE,
}

enum class MediaAssetLookup {
    PRESENT,
    ABSENT,
    UNAVAILABLE,
}

fun missingAssetError(
    hasFullLibraryAccess: Boolean,
    lookup: MediaAssetLookup,
): MediaLibraryError =
    when {
        !hasFullLibraryAccess -> MediaLibraryError.PERMISSION_DENIED
        lookup == MediaAssetLookup.ABSENT -> MediaLibraryError.ASSET_NOT_FOUND
        else -> MediaLibraryError.RESOURCE_UNAVAILABLE
    }

fun mediaReadPermissionMeansFullAccess(
    apiLevel: Int,
    selectedMediaAccessDeclared: Boolean,
    readPermissionGranted: Boolean,
): Boolean =
    readPermissionGranted && (apiLevel < 34 || selectedMediaAccessDeclared)

sealed interface MediaThumbnailOutcome {
    data class Success(val thumbnail: MediaThumbnail) : MediaThumbnailOutcome

    data class Failure(val error: MediaLibraryError) : MediaThumbnailOutcome
}
