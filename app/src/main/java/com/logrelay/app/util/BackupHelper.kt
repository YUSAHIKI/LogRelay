package com.logrelay.app.util

import android.content.Context
import com.logrelay.app.data.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * バックアップを「manifest.json + photos/フォルダ」を含むZIPとして書き出し/読み込みする。
 *
 * 以前はphotoPath(端末内の絶対パス文字列)だけをJSONに保存していたが、
 * それでは写真の実体が失われ、復元してもパスが存在せず写真が消えたように見える問題があった。
 * ZIPに実ファイルを同梱することでこれを解消する。
 */
object BackupHelper {

    suspend fun writeZip(records: List<Record>, output: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(ExportHelper.toBackupJson(records).toByteArray())
            zip.closeEntry()

            records.forEach { record ->
                val path = record.photoPath ?: return@forEach
                val file = File(path)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("photos/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun readZip(context: Context, input: InputStream): List<Record> = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        var manifestRecords: List<Record> = emptyList()
        val extractedFileNames = mutableSetOf<String>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "manifest.json" -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        manifestRecords = ExportHelper.fromBackupJson(text)
                    }
                    entry.name.startsWith("photos/") -> {
                        val fileName = entry.name.removePrefix("photos/")
                        if (fileName.isNotBlank()) {
                            File(photosDir, fileName).outputStream().use { out -> zip.copyTo(out) }
                            extractedFileNames.add(fileName)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // manifest内のphotoPathはファイル名のみが入っているので、
        // 実際に展開できたファイルだけ絶対パスに置き換える(展開できなかった場合はnullにして
        // 「地味に壊れた写真参照」を残さない)
        manifestRecords.map { record ->
            val fileName = record.photoPath
            if (fileName != null && extractedFileNames.contains(fileName)) {
                record.copy(photoPath = File(photosDir, fileName).absolutePath)
            } else {
                record.copy(photoPath = null)
            }
        }
    }
}
