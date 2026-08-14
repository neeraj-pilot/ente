package io.ente.photos.platform.devicetrash

import android.annotation.TargetApi
import android.content.ContentResolver
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

class DeviceTrashService(private val contentResolver: ContentResolver) {
    fun getFiles(): DeviceTrashResult =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DeviceTrashResult.Success(queryFiles())
        } else {
            DeviceTrashResult.Unsupported
        }

    @TargetApi(Build.VERSION_CODES.R)
    private fun queryFiles(): List<TrashedMedia> {
        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Files.FileColumns.DATE_EXPIRES),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
        }
        val cursor = checkNotNull(
            contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                PROJECTION,
                queryArgs,
                null,
            ),
        ) { "MediaStore returned no cursor" }

        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val volumeColumn =
                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.VOLUME_NAME)
            val expiresAtColumn =
                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_EXPIRES)
            val bucketColumn =
                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            buildList {
                while (it.moveToNext()) {
                    add(
                        TrashedMedia(
                            mediaStoreId = it.getLong(idColumn),
                            volumeName = it.getString(volumeColumn),
                            expiresAtEpochSeconds = it.getLong(expiresAtColumn),
                            bucketName = it.getString(bucketColumn),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.VOLUME_NAME,
            MediaStore.Files.FileColumns.DATE_EXPIRES,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        )
    }
}
