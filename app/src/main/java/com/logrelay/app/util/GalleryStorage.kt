package com.logrelay.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * カメラで撮影した写真を、アプリ専用ストレージだけでなく
 * 端末のギャラリー(MediaStore)にもコピーする。
 * ギャラリーアプリ側から見えないと「撮ったのに見当たらない」という
 * 不安につながるため、アプリ内保存とは別に明示的にコピーしておく。
 */
object GalleryStorage {

    /** sourceFile(アプリ内部の写真)を、端末のギャラリーの「Pictures/LogRelay」に保存する */
    fun saveToGallery(context: Context, sourceFile: File): Uri? {
        return try {
            val filename = "LogRelay_${sourceFile.nameWithoutExtension}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LogRelay")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            // ギャラリー保存に失敗しても、アプリ内部の写真は既に保存済みなので致命的ではない
            null
        }
    }
}
