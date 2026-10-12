package com.kaan.play.ui.player

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.settings.SubtitlePreview
import com.kaan.play.ui.settings.SubtitleStyleControls
import com.kaan.play.ui.sources.SourcesSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val HIDE_AFTER_MS = 4_000L

/**
 * Tam ekran oynatıcı. Kontroller sade: geri/ileri 10 sn, oynat/duraklat, ilerleme çubuğu, altyazı, kaynak değiştir.
 * Hepsi dokunmayla ve yön tuşlarıyla (D-pad/klavye/kumanda) kullanılır: kontroller gizliyken Orta/Enter
 * kontrolleri açar, Sol/Sağ 10 sn atlar, medya tuşları doğrudan çalışır. Sürükle-bırak, çift dokunma ya da
 * uzun basma gerekmez. Altyazı menüsündeki "Görünüm" ayarları değişince altyazı oynatma kesilmeden güncellenir.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: PlaybackController,
    target: PlayTarget,
    onBack: () -> Unit,
    onSourcePicked: (PlayableStream) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val subtitleStyle by container.settings.subtitleStyle.collectAsStateWithLifecycle(initialValue = SubtitleStyle())

    val ui = controller.ui
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val dialogOpen = showSources || showSubtitles || showAppearance
    val showControls = controlsVisible || ui.error != null

    fun bump() { interaction++ }
    fun reveal() { controlsVisible = true; bump() }
    fun seek(deltaMs: Long) {
        controller.seekBy(deltaMs)
        position = controller.positionMs()
        reveal()
    }

    // Kontroller bir süre dokunulmazsa (ve oynatma sürerken) kendiliğinden gizlenir.
    LaunchedEffect(controlsVisible, interaction, ui.isPlaying, dialogOpen, ui.error) {
        if (controlsVisible && ui.isPlaying && !dialogOpen && ui.error == null) {
            delay(HIDE_AFTER_MS)
            controlsVisible = false
        }
    }
    // Odak: kontroller açıkken (ya da hata düğmesi varken) oynat/seç düğmesinde, gizliyken kök yüzeyde
    // (yön tuşları orada yakalanır).
    LaunchedEffect(controlsVisible, ui.error) {
        runCatching { if (controlsVisible || ui.error != null) playFocus.requestFocus() else rootFocus.requestFocus() }
    }
    LaunchedEffect(showControls) {
        while (showControls) {
            position = controller.positionMs()
            duration = controller.durationMs()
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                // Geri tuşu burada ele alınır: aralıklı olarak (kontroller açıkken) görünüm ağacındaki bir öğe
                // DOWN olayını tüketiyor ve etkinliğe hiç ulaşmıyordu; kök yakalayıcı ise olayı her zaman görür.
                if (event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) onBack()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                bump()
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { controller.togglePlay(); reveal(); true }
                    Key.MediaFastForward, Key.MediaStepForward -> { seek(SEEK_STEP_MS); true }
                    Key.MediaRewind, Key.MediaStepBackward -> { seek(-SEEK_STEP_MS); true }
                    else -> if (showControls) {
                        false // kontroller açıkken yön tuşları düğmeler arasında gezinir
                    } else when (event.key) {
                        Key.DirectionLeft -> { seek(-SEEK_STEP_MS); true }
                        Key.DirectionRight -> { seek(SEEK_STEP_MS); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> { reveal(); true }
                        else -> false
                    }
                }
            }
            // Açık focusable(): clickable dokunmatik modda odak almaz; gizli kontrollerde tuşlar buraya gelmeli.
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
                bump()
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = controller.player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    // Odak ve tuşlar Compose katmanında; görünüm odak çalmasın.
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            // Ayar değişince anında uygulanır; oynatma kesilmez.
            update = { view -> view.subtitleView?.let { SubtitleStyling.apply(it, subtitleStyle) } },
            modifier = Modifier.fillMaxSize(),
        )

        if (ui.buffering && ui.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
        }

        if (showControls) {
            Controls(
                target = target,
                isPlaying = ui.isPlaying,
                hasError = ui.error != null,
                position = position,
                duration = duration,
                playFocus = playFocus,
                onBack = onBack,
                onTogglePlay = { controller.togglePlay(); bump() },
                onSeek = ::seek,
                onSeekTo = { controller.seekTo(it); position = it; bump() },
                onSubtitles = { showSubtitles = true },
                onSources = { showSources = true },
                onInteract = ::bump,
            )
        }

        ui.error?.let { error ->
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { showSources = true }, modifier = Modifier.focusRequester(playFocus)) {
                    Text(stringResource(R.string.player_error_pick))
                }
            }
        }
    }

    if (showSubtitles) {
        // Seçenekler açılırken bir kez okunur; parça listesi o ana ait anlık görüntüdür.
        val options = remember(ui.tracksVersion) { controller.subtitleOptions() }
        ChoiceDialog(
            title = stringResource(R.string.player_subtitles),
            options = options,
            selected = options.firstOrNull { it.selected } ?: options.first(),
            label = { subtitleLabel(it) },
            onSelect = { controller.selectSubtitle(it); showSubtitles = false },
            onDismiss = { showSubtitles = false },
            footer = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.player_subtitle_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSubtitles = false; showAppearance = true }
                        .padding(vertical = 12.dp),
                )
            },
        )
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text(stringResource(R.string.player_subtitle_appearance)) },
            text = {
                // Yatay ekranda alçak: kaydırılabilir. Önizleme, karartılmış videoda altyazı görünmese de etkiyi gösterir.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SubtitlePreview(subtitleStyle)
                    SubtitleStyleControls(subtitleStyle) { transform ->
                        scope.launch { container.settings.updateSubtitleStyle(transform) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearance = false }) { Text(stringResource(R.string.common_done)) } },
        )
    }

    if (showSources) {
        SourcesSheet(
            request = target.request,
            onDismiss = { showSources = false },
            onPicked = { showSources = false; onSourcePicked(it) },
        )
    }
}

@Composable
private fun Controls(
    target: PlayTarget,
    isPlaying: Boolean,
    hasError: Boolean,
    position: Long,
    duration: Long,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onSources: () -> Unit,
    onInteract: () -> Unit,
) {
    val tint = Color.White
    Box(Modifier.fillMaxSize()) {
        // Üst ve alt kenarda okunabilirlik için yumuşak karartma.
        Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        if (!hasError) {
            Box(Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back), tint = tint)
            }
            Text(
                text = titleOf(target),
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (!hasError) {
                IconButton(onClick = onSubtitles) {
                    Icon(PlayIcons.Subtitles, contentDescription = stringResource(R.string.player_subtitles), tint = tint)
                }
            }
            IconButton(onClick = onSources) {
                Icon(PlayIcons.SwitchSource, contentDescription = stringResource(R.string.player_sources), tint = tint)
            }
        }

        if (!hasError) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeekButton(PlayIcons.SeekBack, stringResource(R.string.player_rewind), tint) { onSeek(-10_000L) }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(72.dp).focusRequester(playFocus),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) PlayIcons.Pause else PlayIcons.PlayTriangle,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                SeekButton(PlayIcons.SeekForward, stringResource(R.string.player_forward), tint, iconFirst = false) { onSeek(10_000L) }
            }

            ProgressBar(
                position = position,
                duration = duration,
                onSeekTo = onSeekTo,
                onInteract = onInteract,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SeekButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    iconFirst: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(width = 72.dp, height = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(R.string.player_seconds)
            if (!iconFirst) Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
            Icon(icon, contentDescription = description, tint = tint)
            if (iconFirst) Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

@Composable
private fun ProgressBar(
    position: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragFraction else fraction

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime((shown * duration).toLong()), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragFraction = it; onInteract() },
            onValueChangeFinished = {
                if (duration > 0) onSeekTo((dragFraction * duration).toLong())
                dragging = false
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(formatTime(duration), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun titleOf(target: PlayTarget): String {
    val r = target.request
    return if (r.type == MediaType.Tv && r.season != null && r.episode != null) {
        stringResource(R.string.player_title_episode, target.title, r.season, r.episode)
    } else {
        target.title
    }
}

@Composable
private fun subtitleLabel(option: SubtitleOption): String {
    if (option.index == null) return stringResource(R.string.player_subtitles_off)
    val name = languageName(option.language)
        ?: return stringResource(R.string.player_subtitle_number, option.index + 1)
    return if (option.ordinal > 1) stringResource(R.string.dot_join, name, option.ordinal.toString()) else name
}

/** "tur"/"tr" -> "Türkçe" (arayüz dilinde); tanınmayan/boş etiket null döner. */
private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    val ui = Locale.forLanguageTag(AppLanguage.effective().iso)
    val name = Locale.forLanguageTag(code).getDisplayLanguage(ui)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(ui) }
}

private fun PlaybackError.messageRes(): Int = when (this) {
    PlaybackError.Network -> R.string.player_error_network
    PlaybackError.Unavailable -> R.string.player_error_unavailable
    PlaybackError.Format -> R.string.player_error_format
}

/** 75_000 -> "1:15", 3_725_000 -> "1:02:05". */
internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
