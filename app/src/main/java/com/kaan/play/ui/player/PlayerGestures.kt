package com.kaan.play.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import kotlinx.coroutines.delay

private const val INDICATOR_HIDE_MS = 1_000L

/**
 * Kaydırma jestinin sayılmayacağı alanlar (kontrol çubukları, düğmeler, ilerleme çubuğu). Her alan ekranda
 * olduğu sürece konumunu bildirir, ekrandan kalkınca (kontroller gizlenince) silinir.
 */
@Stable
class GestureZones {
    private val rects = HashMap<Any, Rect>()

    fun set(key: Any, rect: Rect) { rects[key] = rect }
    fun remove(key: Any) { rects.remove(key) }
    fun contains(point: Offset): Boolean = rects.values.any { it.contains(point) }
}

/** Bu öğenin kapladığı alanı kaydırma jestinden dışlar. */
@Composable
fun Modifier.gestureZone(zones: GestureZones, key: Any): Modifier {
    DisposableEffect(zones, key) { onDispose { zones.remove(key) } }
    return onGloballyPositioned { zones.set(key, it.boundsInRoot()) }
}

/** Göstergenin durumu: hangi düzey, yüzde ve parmak ekranda mı. Parmak kalkınca 1 sn sonra gizlenir. */
@Stable
class LevelIndicatorState {
    var kind by mutableStateOf<LevelKind?>(null)
        internal set
    var percent by mutableIntStateOf(0)
        internal set
    var held by mutableStateOf(false)
        internal set
    internal var version by mutableIntStateOf(0)

    internal fun show(kind: LevelKind, level: Float) {
        this.kind = kind
        percent = LevelRules.percent(level)
        held = true
        version++
    }

    internal fun release() { held = false }
}

/**
 * Ekranın sağ yarısında dikey kaydırma sesi, sol yarısında bu pencerenin parlaklığını değiştirir. Başlangıç
 * değeri gerçek ses/parlaklıktır; dokunma eşiği aşılana dek bir şey olmaz, sonra kayma orantılı ilerler.
 * [levels] null ise (TV/kumanda) hiçbir şey eklenmez. Tek dokunuşu bozmamak için `clickable`dan SONRA
 * (daha içte) eklenir: eşik aşılınca olayı tüketir, dokunma tıklama sayılmaz.
 */
@Composable
fun Modifier.levelGestures(levels: DeviceLevels?, zones: GestureZones, indicator: LevelIndicatorState): Modifier {
    if (levels == null) return this
    return pointerInput(levels, zones, indicator) {
        var kind: LevelKind? = null
        var level = 0f
        detectVerticalDragGestures(
            onDragStart = { start ->
                kind = LevelRules.kindAt(start.x, size.width.toFloat())
                    .takeIf { !zones.contains(start) && (it != LevelKind.Volume || levels.volumeAdjustable) }
                kind?.let {
                    level = levels.level(it)
                    indicator.show(it, level)
                }
            },
            onVerticalDrag = { _, dy ->
                kind?.let {
                    level = LevelRules.move(level, -dy, size.height.toFloat())
                    levels.set(it, level)
                    // Gösterge gerçek değeri gösterir (ses tamsayı basamaktır).
                    indicator.show(it, levels.level(it))
                }
            },
            onDragEnd = { kind = null; indicator.release() },
            onDragCancel = { kind = null; indicator.release() },
        )
    }
}

/** Kaydırırken çıkan küçük gösterge: ikon + yüzde, ilgili yarıda durur; jest bitince 1 sn sonra kaybolur. */
@Composable
fun LevelIndicator(state: LevelIndicatorState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.held, state.version) {
        if (!state.held && state.kind != null) {
            delay(INDICATOR_HIDE_MS)
            state.kind = null
        }
    }
    val kind = state.kind ?: return
    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 40.dp)) {
        Row(
            modifier = Modifier
                .align(if (kind == LevelKind.Volume) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (kind == LevelKind.Volume) PlayIcons.Volume else PlayIcons.Brightness,
                contentDescription = stringResource(if (kind == LevelKind.Volume) R.string.player_volume else R.string.player_brightness),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.player_level_percent, state.percent),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
