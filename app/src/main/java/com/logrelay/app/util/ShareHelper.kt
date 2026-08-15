package com.logrelay.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Markdown/CSVのテキストを一時ファイルとしてキャッシュに書き出し、
 * Androidの共有シート経由でObsidian・メール・Google Driveなど
 * 好きなアプリに直接渡せるようにする。
 *
 * SAF(CreateDocument)による「端末に保存」よりも、
 * 「他アプリへその場で渡す」という目的にはこちらの方が手数が少ない。
 */
object ShareHelper {

    fun shareTextFile(context: Context, filename: String, mimeType: String, content: String) {
        val cacheDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        val file = File(cacheDir, filename)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            // text/markdown等の細かい型だと対応アプリの登録から漏れることがあるため、
            // 互換性の広いtext/plainで送る(拡張子.mdはそのまま保たれる)
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ファイルを開けない・受け取れないアプリ向けに、本文テキストも一緒に渡しておく
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "共有先を選択"))
    }
}
