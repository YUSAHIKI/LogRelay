package com.logrelay.app.util

import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFCタグによる記録トリガーの共通ロジック。
 *
 * 将来WearOS Companion等の別トリガー手段が加わることを見据え、
 * Intent判定・NDEFメッセージの組み立て・タグへの書き込みをActivityのライフサイクル管理から
 * 独立させてある(Activity側はenableForegroundDispatch/enableReaderModeの呼び出しのみ担当)。
 *
 * 複数タグの識別・使い分けはスコープ外。1枚のNFCタグ＝1つの汎用トリガーとして扱う。
 * ペイロードはMIMEタイプによる排他制御が主目的で、中身自体に意味は持たせていない。
 */
object NfcTrigger {
    /** 他アプリのタグへの誤反応・LogRelay用タグへの他アプリ誤反応を防ぐための独自MIMEタイプ */
    const val MIME_TYPE = "application/vnd.logrelay.trigger"

    private val TRIGGER_PAYLOAD = "logrelay-trigger".toByteArray(Charsets.UTF_8)

    /** 渡されたIntentがLogRelayのNFCトリガー(NDEF, 独自MIMEタイプ)によるものかどうか */
    fun isTriggerIntent(intent: Intent?): Boolean {
        return intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED && intent.type == MIME_TYPE
    }

    /** enableForegroundDispatchに渡す、LogRelay独自MIMEタイプのみに絞ったIntentFilter */
    fun createIntentFilter(): IntentFilter {
        val filter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            filter.addDataType(MIME_TYPE)
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("MIMEタイプの指定が不正です: $MIME_TYPE", e)
        }
        return filter
    }

    /** タグに書き込むNDEFメッセージ(独自MIMEタイプ＋最小限のペイロード) */
    fun buildTriggerNdefMessage(): NdefMessage {
        return NdefMessage(arrayOf(NdefRecord.createMime(MIME_TYPE, TRIGGER_PAYLOAD)))
    }

    /**
     * 検出したタグにトリガー用NDEFメッセージを書き込む。
     * 既にNDEF初期化済みのタグは`Ndef`で、未初期化タグは`NdefFormatable`で書き込む。
     */
    suspend fun writeTriggerTag(tag: Tag): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val message = buildTriggerNdefMessage()
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (!ndef.isWritable) {
                        throw IOException("このタグは書き込みできません")
                    }
                    if (ndef.maxSize < message.toByteArray().size) {
                        throw IOException("タグの容量が不足しています")
                    }
                    ndef.writeNdefMessage(message)
                } finally {
                    ndef.close()
                }
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: throw IOException("このタグはNDEF形式に対応していません")
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    formatable.close()
                }
            }
        }
    }
}
