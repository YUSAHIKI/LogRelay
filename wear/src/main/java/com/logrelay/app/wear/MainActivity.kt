package com.logrelay.app.wear

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

private const val PREFS_NAME = "logrelay_wear_settings"
private const val KEY_HAS_REQUESTED_LOCATION_PERMISSION = "has_requested_location_permission"

// 「受付済み」等のフィードバックをどれだけ見せてから終了するか。
// 具体値は設計仕様書7節の通り未確定。短すぎると見逃し、長すぎると連続起動の妨げになるため、
// 実機での二回押し操作を踏まえて調整する前提の暫定値。
private const val FEEDBACK_DURATION_MS = 1200L

/**
 * ウォッチ側の唯一の画面。「起動＝記録、1起動1記録」方式(設計仕様書5.1〜5.2節)を採用している。
 *
 * - 記録の実行は`onCreate()`(実質的にはComposeの初回コンポジション)でのみ行う。
 *   `onResume()`/`onStart()`では行わない(権限ダイアログからの復帰や画面切り替えのたびに
 *   記録が重複発生するのを防ぐため)。
 * - `savedInstanceState == null`の場合のみ自動送信する。Activity再生成
 *   (プロセス再生成・コンフィグ変更等)でonCreate()が再度呼ばれた場合の重複記録を防ぐ。
 * - 記録完了(または失敗)のフィードバックを短時間表示した後、`finishAndRemoveTask()`で
 *   画面とタスクを終了する。タスクが残ったままだと、次のホームキー二回押しが「新規起動」ではなく
 *   「既存画面の再表示」になり記録が発火しない問題があったため(付録C.7)。
 * - 「もう一度記録」ボタンは置かない。表示中にタップできてしまうと、同一起動で二重記録が
 *   発生する窓を意図的に開けることになるため、1起動1記録に統一する。
 * - 位置情報権限は初回セットアップ時(端末上でこのアプリを一度も起動したことがない場合)のみ要求する。
 *   毎回ダイアログを出すと、ユーザー応答待ちと自動終了が競合するため。
 *
 * この方式を採る理由: Galaxy Watchのホームキー二回押しは「アプリ」単位でしか割り当てできず、
 * ランチャー起動と同一のIntent(extraなし)でこのActivityが起動される。記録専用の別Activityを
 * 用意しても割り当て先として選択できないため、このActivity自体を記録トリガーとする必要がある。
 *
 * 副作用として、ランチャー／アプリ一覧からの通常起動でも記録される。Wear側アプリは記録以外の
 * 機能(履歴閲覧・設定等)を持たないため、「起動＝記録意図」と見なして許容する
 * (誤操作による記録はLogRelay側で削除可能なため実害は限定的、という判断)。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isFreshLaunch = savedInstanceState == null
        setContent {
            TriggerScreen(
                shouldAutoSend = isFreshLaunch,
                onFinished = { finishAndRemoveTask() }
            )
        }
    }
}

private sealed class TapStatus {
    data object Idle : TapStatus()
    data object Sending : TapStatus()
    data class Sent(val source: String) : TapStatus()
    data class Failed(val message: String) : TapStatus()
}

@Composable
private fun TriggerScreen(shouldAutoSend: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<TapStatus>(TapStatus.Idle) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒否されてもmanual_pendingにフォールバックするため、結果分岐は不要 */ }

    // shouldAutoSend=trueの場合のみ、初回コンポジションで1回だけ記録処理を実行する
    LaunchedEffect(Unit) {
        if (!shouldAutoSend) return@LaunchedEffect

        if (!hasRequestedLocationPermissionBefore(context)) {
            markLocationPermissionRequested(context)
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        status = TapStatus.Sending
        status = when (val result = WatchTriggerSender.sendTrigger(context)) {
            is WatchTriggerSender.Result.Sent -> TapStatus.Sent(result.source)
            is WatchTriggerSender.Result.Failed -> TapStatus.Failed(result.message)
        }
    }

    // フィードバック表示後、finishAndRemoveTask()で終了する。1起動1記録のため、
    // 送信後にIdleへ戻して再送信を待つ、ということはしない
    LaunchedEffect(status) {
        if (status is TapStatus.Sent || status is TapStatus.Failed) {
            delay(FEEDBACK_DURATION_MS)
            onFinished()
        }
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = statusText(status),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.title3
                )
            }
        }
    }
}

private fun hasRequestedLocationPermissionBefore(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_HAS_REQUESTED_LOCATION_PERMISSION, false)
}

private fun markLocationPermissionRequested(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_HAS_REQUESTED_LOCATION_PERMISSION, true).apply()
}

private fun statusText(status: TapStatus): String = when (status) {
    is TapStatus.Idle -> "記録中…"
    is TapStatus.Sending -> "記録中…"
    is TapStatus.Sent -> "受付済み"
    is TapStatus.Failed -> "送信できませんでした"
}
