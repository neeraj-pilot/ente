package io.ente.photos.platform.media

import android.Manifest
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MediaThumbnailService(context: Context) {
    private val context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver
    private val selectedMediaAccessDeclared =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            declaresPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    private class Operation {
        val cancellationSignal = CancellationSignal()
        private val settlementCallbacks = mutableListOf<() -> Unit>()
        private var settled = false

        @Volatile
        var task: Runnable? = null

        fun cancel() {
            cancellationSignal.cancel()
        }

        fun whenSettled(callback: () -> Unit) {
            val runNow = synchronized(this) {
                if (settled) {
                    true
                } else {
                    settlementCallbacks.add(callback)
                    false
                }
            }
            if (runNow) callback()
        }

        fun markSettled() {
            val callbacks = synchronized(this) {
                if (settled) return
                settled = true
                val callbacks = settlementCallbacks.toList()
                settlementCallbacks.clear()
                callbacks
            }
            callbacks.forEach { it() }
        }
    }

    private val closed = AtomicBoolean(false)
    private val executor = ThreadPoolExecutor(
        2,
        2,
        0,
        TimeUnit.MILLISECONDS,
        NewestFirstTaskQueue(32),
    )
    private val registry = MediaOperationRegistry<Operation, MediaThumbnailOutcome>(34)

    fun load(
        operationId: String,
        request: MediaThumbnailRequest,
        completion: (MediaThumbnailOutcome) -> Unit,
    ) {
        if (closed.get()) {
            completion(MediaThumbnailOutcome.Failure(MediaLibraryError.PLATFORM_FAILURE))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            completion(MediaThumbnailOutcome.Failure(MediaLibraryError.UNSUPPORTED_PLATFORM))
            return
        }
        val operation = Operation()
        if (!registry.register(operationId, operation, completion)) {
            completion(MediaThumbnailOutcome.Failure(MediaLibraryError.BUSY))
            return
        }
        val task = SettlingTask(
            shouldRun = { registry.contains(operationId) },
            compute = { load(request, operation.cancellationSignal) },
            onSettled = operation::markSettled,
            publish = { outcome -> registry.complete(operationId, outcome) },
        )
        operation.task = task
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            registry.complete(
                operationId,
                MediaThumbnailOutcome.Failure(MediaLibraryError.BUSY),
            )
            operation.markSettled()
        }
    }

    fun cancel(operationId: String, onSettled: () -> Unit) {
        val operation = registry.state(operationId)
        if (operation == null) {
            onSettled()
            return
        }
        operation.whenSettled(onSettled)
        if (
            registry.complete(
                operationId,
                MediaThumbnailOutcome.Failure(MediaLibraryError.CANCELLED),
            ) != null
        ) {
            operation.cancel()
            if (operation.task?.let(executor::remove) == true) {
                operation.markSettled()
            }
            executor.purge()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        registry.removeAll().forEach {
            it.cancel()
            it.markSettled()
        }
        executor.shutdownNow()
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun load(
        request: MediaThumbnailRequest,
        cancellationSignal: CancellationSignal,
    ): MediaThumbnailOutcome =
        try {
            val uri = assetUri(request)
                ?: return MediaThumbnailOutcome.Failure(MediaLibraryError.INVALID_REQUEST)
            val source = resolver.loadThumbnail(
                uri,
                Size(request.widthPx, request.heightPx),
                cancellationSignal,
            )
            try {
                cancellationSignal.throwIfCanceled()
                val normalized = normalize(source, request)
                try {
                    cancellationSignal.throwIfCanceled()
                    val bytes = ByteArrayOutputStream().use { stream ->
                        if (!normalized.compress(Bitmap.CompressFormat.JPEG, request.quality, stream)) {
                            return MediaThumbnailOutcome.Failure(MediaLibraryError.UNSUPPORTED_FORMAT)
                        }
                        stream.toByteArray()
                    }
                    if (bytes.isEmpty()) {
                        MediaThumbnailOutcome.Failure(MediaLibraryError.UNSUPPORTED_FORMAT)
                    } else {
                        MediaThumbnailOutcome.Success(
                            MediaThumbnail(bytes, normalized.width, normalized.height),
                        )
                    }
                } finally {
                    normalized.recycle()
                }
            } finally {
                source.recycle()
            }
        } catch (_: OperationCanceledException) {
            MediaThumbnailOutcome.Failure(MediaLibraryError.CANCELLED)
        } catch (_: SecurityException) {
            MediaThumbnailOutcome.Failure(MediaLibraryError.PERMISSION_DENIED)
        } catch (_: FileNotFoundException) {
            MediaThumbnailOutcome.Failure(fileNotFoundError(request))
        } catch (_: IOException) {
            MediaThumbnailOutcome.Failure(MediaLibraryError.RESOURCE_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            MediaThumbnailOutcome.Failure(MediaLibraryError.INVALID_REQUEST)
        } catch (_: RuntimeException) {
            MediaThumbnailOutcome.Failure(MediaLibraryError.PLATFORM_FAILURE)
        }

    private fun fileNotFoundError(request: MediaThumbnailRequest): MediaLibraryError {
        val hasFullAccess = hasFullLibraryAccess(request.kind)
        if (!hasFullAccess) return missingAssetError(false, MediaAssetLookup.UNAVAILABLE)
        val uri = assetUri(request) ?: return MediaLibraryError.RESOURCE_UNAVAILABLE
        val lookup = try {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) MediaAssetLookup.PRESENT else MediaAssetLookup.ABSENT
            } ?: MediaAssetLookup.UNAVAILABLE
        } catch (_: SecurityException) {
            return MediaLibraryError.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            MediaAssetLookup.UNAVAILABLE
        }
        return missingAssetError(true, lookup)
    }

    private fun assetUri(request: MediaThumbnailRequest): Uri? {
        val mediaId = request.assetId.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val collection = when (request.kind) {
            MediaAssetKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaAssetKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, mediaId)
    }

    private fun hasFullLibraryAccess(kind: MediaAssetKind): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (kind) {
                MediaAssetKind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
                MediaAssetKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            }
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return mediaReadPermissionMeansFullAccess(
            apiLevel = Build.VERSION.SDK_INT,
            selectedMediaAccessDeclared = selectedMediaAccessDeclared,
            readPermissionGranted =
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    @Suppress("DEPRECATION")
    private fun declaresPermission(permission: String): Boolean =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(permission) == true

    private fun normalize(source: Bitmap, request: MediaThumbnailRequest): Bitmap {
        require(source.width > 0 && source.height > 0)
        val width: Int
        val height: Int
        val destination: RectF
        when (request.fit) {
            MediaThumbnailFit.CONTAIN -> {
                val scale = min(
                    request.widthPx.toDouble() / source.width,
                    request.heightPx.toDouble() / source.height,
                )
                width = max(1, (source.width * scale).roundToInt())
                height = max(1, (source.height * scale).roundToInt())
                destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
            }
            MediaThumbnailFit.COVER -> {
                width = request.widthPx
                height = request.heightPx
                val scale = max(
                    width.toDouble() / source.width,
                    height.toDouble() / source.height,
                )
                val drawnWidth = (source.width * scale).toFloat()
                val drawnHeight = (source.height * scale).toFloat()
                destination = RectF(
                    (width - drawnWidth) / 2,
                    (height - drawnHeight) / 2,
                    (width + drawnWidth) / 2,
                    (height + drawnHeight) / 2,
                )
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.BLACK)
                drawBitmap(
                    source,
                    null,
                    destination,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
                )
            }
            return bitmap
        } catch (error: RuntimeException) {
            bitmap.recycle()
            throw error
        }
    }
}

internal class NewestFirstTaskQueue(capacity: Int) : LinkedBlockingDeque<Runnable>(capacity) {
    override fun offer(element: Runnable): Boolean = offerFirst(element)
}

internal class SettlingTask<T>(
    private val shouldRun: () -> Boolean,
    private val compute: () -> T,
    private val onSettled: () -> Unit,
    private val publish: (T) -> Unit,
) : Runnable {
    override fun run() {
        var didSettle = false
        fun settle() {
            if (didSettle) return
            didSettle = true
            onSettled()
        }
        try {
            if (!shouldRun()) return
            val value = compute()
            settle()
            publish(value)
        } finally {
            settle()
        }
    }
}
