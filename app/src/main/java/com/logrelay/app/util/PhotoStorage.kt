package com.logrelay.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Photo Pickerで選んだ画像のUriは、アプリ再起動後には読めなくなることがあるため、
 * アプリの内部ストレージ(filesDir/photos/)に実体をコピーして保持する。
 * 1ログにつき1枚の運用なので、上書き時は古いファイルを削除してから差し替える。
 */
object PhotoStorage {

    private fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    /** 選択されたUriの中身を内部ストレージにコピーし、保存先の絶対パスを返す */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val destFile = File(photosDir(context), "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** 記録から写真を外すときに、実体ファイルも削除する */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /**
     * カメラアプリに「ここに撮影結果を書き込んで」と渡すための一時ファイルUriを発行する。
     * 撮影成功後は、この一時ファイルのUriをcopyToInternalStorageに渡して
     * 通常の添付フローに合流させる。
     */
    fun createCaptureUri(context: Context): Uri {
        val capturesDir = File(context.cacheDir, "captures").apply { if (!exists()) mkdirs() }
        val file = File(capturesDir, "${UUID.randomUUID()}.jpg")
        file.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * 一覧表示用の軽量サムネイルを作る：目的サイズに合わせて縮小デコードしたうえで、
     * 中心を正方形にクロップする。フルサイズの画像をそのまま読み込むとメモリ・
     * 描画コストが大きいため、一覧のスクロール性能を保つには縮小が必須。
     */
    fun decodeSquareThumbnail(path: String, targetPx: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(boundsOptions, targetPx, targetPx)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null

            val cropSize = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - cropSize) / 2
            val y = (bitmap.height - cropSize) / 2
            Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

