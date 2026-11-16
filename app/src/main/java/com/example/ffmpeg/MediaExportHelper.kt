package com.example.ffmpeg

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaExportHelper {

    private val mediaStoreVideoMimes = setOf(
        "video/mp4",
        "video/3gpp",
        "video/webm",
        "video/x-msvideo"
    )

    private val mediaStoreAudioMimes = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "audio/opus",
        "audio/wav"
    )

    fun getAppOutputDirectory(context: Context): File {
        val dir = File(context.filesDir, "converted")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun exportToPublicMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        isAudioOnly: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        val resolver = context.contentResolver

        val (primaryCollection, primaryFolder) = when {
            isAudioOnly && mimeType in mediaStoreAudioMimes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MUSIC}/KonvertVideo"
            !isAudioOnly && mimeType in mediaStoreVideoMimes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/KonvertVideo"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_DOWNLOADS}/KonvertVideo"
            else ->
                MediaStore.Files.getContentUri("external") to "${Environment.DIRECTORY_DOWNLOADS}/KonvertVideo"
        }

        val inserted = insertAndCopy(resolver, primaryCollection, primaryFolder, sourceFile, displayName, mimeType)
        if (inserted != null) return@withContext inserted

        // Fallback to MediaStore.Downloads if OEM MediaStore rejects the specific video/audio MIME type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return@withContext insertAndCopy(
                resolver = resolver,
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/KonvertVideo",
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType
            )
        }
        null
    }

    private fun insertAndCopy(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativeFolder: String,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                runCatching { resolver.update(uri, values, null, null) }
            }
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun getShareUri(context: Context, filePath: String, galleryUri: String?): Uri? {
        val file = File(filePath)
        if (file.exists()) {
            val providerUri = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrNull()
            if (providerUri != null) return providerUri
        }
        return galleryUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    suspend fun saveBitmapToPictures(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KonvertVideo")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext null
        try {
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
            if (!wrote) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                runCatching { resolver.update(uri, values, null, null) }
            }
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun buildShareIntent(context: Context, filePath: String, galleryUri: String?, mimeType: String): Intent? {
        val shareUri = getShareUri(context, filePath, galleryUri) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildMultiShareIntent(
        context: Context,
        items: List<Triple<String, String?, String>>
    ): Intent? {
        val uris = ArrayList<Uri>()
        val mimeTypes = mutableSetOf<String>()
        items.forEach { (filePath, galleryUri, mime) ->
            val shareUri = getShareUri(context, filePath, galleryUri)
            if (shareUri != null) {
                uris.add(shareUri)
                mimeTypes.add(mime)
            }
        }
        if (uris.isEmpty()) return null
        if (uris.size == 1) {
            return Intent(Intent.ACTION_SEND).apply {
                type = mimeTypes.firstOrNull() ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val commonType = when {
            mimeTypes.all { it.startsWith("video/") } -> "video/*"
            mimeTypes.all { it.startsWith("audio/") } -> "audio/*"
            else -> "*/*"
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

