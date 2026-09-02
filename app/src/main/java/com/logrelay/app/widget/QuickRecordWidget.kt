package com.logrelay.app.widget

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import android.content.Context
import com.logrelay.app.R
import com.logrelay.app.data.RecordRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// アプリ本体のテーマ(RelayLabデザイン基盤: Primaryインディゴ×Neutralオフホワイト)と統一。
// day/nightは区別せず同じ色を使う。形状・タップ挙動は変更せず、配色のみ新トークンに合わせている。
private val WidgetIndigo = ColorProvider(day = Color(0xFF1A237E), night = Color(0xFF1A237E))
private val WidgetPaper = ColorProvider(day = Color(0xFFF9F9F8), night = Color(0xFFF9F9F8))

// フィードバック表示中かどうか／直近の記録時刻を保持するGlance state用のキー
private val FEEDBACK_TEXT_KEY = stringPreferencesKey("feedback_text")
private val LAST_TIME_KEY = stringPreferencesKey("last_time")

// 「記録しました」の表示をどれくらい見せてから元に戻すか
private const val FEEDBACK_DURATION_MS = 1300L

/**
 * ウィジェットA：即記録型
 * タップ→アイコン選択なし→位置＋時刻を即保存。
 * 保存直後は一瞬チェックマーク＋時刻を表示し、その後は「前回 HH:mm」という
 * 控えめな表示に切り替わる。これにより、開かなくても最後の記録時刻がひと目で分かる。
 */
class QuickRecordWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val feedbackText = prefs[FEEDBACK_TEXT_KEY]
    val lastTime = prefs[LAST_TIME_KEY]

    // 角丸+単色の背景。以前は二重トーンの縁取りを試みたが、
    // 端末(ランチャー)によって描画が崩れる不具合が出たため、安定を優先して単層に戻した。
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetIndigo)
            .cornerRadius(20.dp)
            .clickable(actionRunCallback<RecordNowAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        if (feedbackText != null) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WidgetPaper),
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = feedbackText,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetPaper,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_plus),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WidgetPaper),
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            // 前回記録があれば時刻を、なければ「記録」ラベルを1行だけ表示する。
            // 1x1の狭いスペースに収めるため、アイコン+1行という構成に絞っている。
            Text(
                text = lastTime?.let { "前回 $it" } ?: "記録",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetPaper
                )
            )
        }
    }
}

/**
 * ウィジェットタップ時に呼ばれるアクション。
 *
 * 処理の流れ：
 * 1. 位置＋時刻を記録
 * 2. チェックマーク＋時刻を一瞬表示するようウィジェットの状態を更新
 * 3. FEEDBACK_DURATION_MS だけ待ってから、表示を「前回 HH:mm」に切り替える
 *    (完全に消すのではなく、直近の記録時刻を残すことでウィジェット自体に情報を持たせる)
 */
class RecordNowAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val repository = RecordRepository(context)
        val record = repository.captureNow()

        val timeText = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(record.timestamp))
        val hasLocation = record.latitude != null && record.longitude != null
        val feedback = if (hasLocation) timeText else "$timeText (位置情報なし)"

        updateAppWidgetState(context, glanceId) { p ->
            p[FEEDBACK_TEXT_KEY] = feedback
        }
        QuickRecordWidget().update(context, glanceId)

        delay(FEEDBACK_DURATION_MS)

        updateAppWidgetState(context, glanceId) { p ->
            p.remove(FEEDBACK_TEXT_KEY)
            p[LAST_TIME_KEY] = timeText
        }
        QuickRecordWidget().update(context, glanceId)
    }
}
