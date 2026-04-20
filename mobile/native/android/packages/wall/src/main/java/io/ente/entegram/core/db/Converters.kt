package io.ente.entegram.core.db

import androidx.room.TypeConverter
import io.ente.entegram.core.models.AspectPreset
import io.ente.entegram.core.models.CaptionPayload
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // --- Instant ↔ Long (epoch millis) ---

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // --- FollowRequest.Direction ↔ String ---

    @TypeConverter
    fun fromDirection(value: FollowRequest.Direction): String = value.name

    @TypeConverter
    fun toDirection(value: String): FollowRequest.Direction =
        FollowRequest.Direction.valueOf(value)

    // --- List<PostAsset> ↔ String (JSON) ---

    @TypeConverter
    fun fromPostAssets(value: List<PostAsset>): String =
        json.encodeToString(value.map { it.toSerializable() })

    @TypeConverter
    fun toPostAssets(value: String): List<PostAsset> =
        json.decodeFromString<List<PostAssetSurrogate>>(value).map { it.toModel() }

    // --- List<CaptionPayload.Entry> ↔ String (JSON) ---

    @TypeConverter
    fun fromCaptionImages(value: List<CaptionPayload.Entry>): String =
        json.encodeToString(value.map { it.toSerializable() })

    @TypeConverter
    fun toCaptionImages(value: String): List<CaptionPayload.Entry> =
        json.decodeFromString<List<CaptionEntrySurrogate>>(value).map { it.toModel() }
}

// Serializable surrogates — the domain models aren't @Serializable, so we
// use lightweight wrappers for the Room JSON columns only.

@Serializable
private data class PostAssetSurrogate(
    val position: Int,
    val variant: String,
    val objectKey: String,
    val blurHash: String,
    val aspect: String,
)

private fun PostAsset.toSerializable() = PostAssetSurrogate(
    position = position,
    variant = variant.name,
    objectKey = objectKey,
    blurHash = blurHash,
    aspect = aspect.name,
)

private fun PostAssetSurrogate.toModel() = PostAsset(
    position = position,
    variant = PostAssetVariant.valueOf(variant),
    objectKey = objectKey,
    blurHash = blurHash,
    aspect = AspectPreset.valueOf(aspect),
)

@Serializable
private data class CaptionEntrySurrogate(
    val position: Int,
    val blurHash: String,
    val aspect: String,
)

private fun CaptionPayload.Entry.toSerializable() = CaptionEntrySurrogate(
    position = position,
    blurHash = blurHash,
    aspect = aspect.name,
)

private fun CaptionEntrySurrogate.toModel() = CaptionPayload.Entry(
    position = position,
    blurHash = blurHash,
    aspect = AspectPreset.valueOf(aspect),
)
