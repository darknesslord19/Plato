package com.kaan.play.ui.player

import android.graphics.Color as AndroidColor
import android.os.SystemClock
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.kaan.play.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import com.kaan.play.ui.components.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.requestWhenAttached
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val HIDE_AFTER_MS = 4_000L
private const val RESUME_NOTICE_MS = 5_000L
private const val SEEK_HOLD_MS = 1_200L
private const val NOTICE_MS = 4_000L

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
    subtitles: SubtitleController,
    target: PlayTarget,
    showResumeNotice: Boolean,
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
    var showMenu by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var lastSeekKeyAt by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // "Kaldığın yerden devam ediliyor · Baştan başla": ~5 sn; düğmede odak varken (kumanda) süre işlemez.
    var resumeNotice by remember { mutableStateOf(showResumeNotice) }
    var noticeFocused by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val noticeFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var barFocused by remember { mutableStateOf(false) }
    val dialogOpen = showSources || showSubtitles || showAppearance || showMenu || showSync
    // Senkron paneli açıkken kontroller gizlenir: altyazı (altta) ilerleme çubuğunun altında kalmasın.
    val showControls = (controlsVisible && !showSync) || ui.error != null

    fun bump() { interaction++ }
    fun reveal() { controlsVisible = true; bump() }
    fun seek(deltaMs: Long) {
        controller.seekBy(deltaMs)
        position = controller.positionMs()
        reveal()
    }
    fun seekByKey(direction: Int, repeat: Int) {
        val step = SeekAcceleration.stepMs(repeat)
        lastSeekKeyAt = SystemClock.uptimeMillis()
        if (step > 0) seek(direction * step) else bump()
    }

    // Kontroller bir süre dokunulmazsa (ve oynatma sürerken) kendiliğinden gizlenir.
    LaunchedEffect(controlsVisible, interaction, ui.isPlaying, dialogOpen, ui.error) {
        if (controlsVisible && ui.isPlaying && !dialogOpen && ui.error == null) {
            delay(HIDE_AFTER_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(resumeNotice, noticeFocused) {
        if (resumeNotice && !noticeFocused) {
            delay(RESUME_NOTICE_MS)
            resumeNotice = false
        }
    }
    LaunchedEffect(ui.error) { if (ui.error != null) resumeNotice = false }
    LaunchedEffect(ui.tracksVersion) { subtitles.onTracksChanged() }
    // "Altyazı yüklenemedi" kısa süre görünür.
    LaunchedEffect(subtitles.status) {
        if (subtitles.status == SubtitleStatus.Failed) {
            delay(NOTICE_MS)
            subtitles.clearStatus()
        }
    }
    // Odak: kontroller açıkken ilerleme çubuğunda (hata varsa hata düğmesinde), gizliyken kök yüzeyde
    // (yön tuşları orada yakalanır).
    LaunchedEffect(controlsVisible, ui.error) {
        when {
            ui.error != null -> runCatching { playFocus.requestFocus() }
            controlsVisible -> barFocus.requestWhenAttached()
            else -> runCatching { rootFocus.requestFocus() }
        }
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
                val repeat = event.nativeKeyEvent.repeatCount
                // Sarma sürerken (basılı tutma) kontroller açılsa da Sol/Sağ sarmaya devam eder, odağı gezdirmez.
                val seeking = SystemClock.uptimeMillis() - lastSeekKeyAt < SEEK_HOLD_MS
                when (PlayerKeys.actionFor(event.key, controlsVisible = showControls && (!seeking || barFocused), progressFocused = barFocused)) {
                    PlayerKeyAction.PassThrough -> false
                    PlayerKeyAction.TogglePlay -> { if (repeat == 0) controller.togglePlay(); reveal(); true }
                    PlayerKeyAction.SeekBack -> { seekByKey(-1, repeat); true }
                    PlayerKeyAction.SeekForward -> { seekByKey(1, repeat); true }
                    PlayerKeyAction.Reveal -> { reveal(); true }
                    PlayerKeyAction.OpenMenu -> { reveal(); showMenu = true; true }
                    PlayerKeyAction.GoToButtons -> { runCatching { playFocus.requestFocus() }; true }
                    PlayerKeyAction.LeaveBarUp -> focusManager.moveFocus(FocusDirection.Up)
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
                    // Harici altyazı satırlarını (kaydırmayla birlikte) kendimiz veririz; videonun içindekileri Media3 çizer.
                    subtitles.attach { cues -> subtitleView?.setCues(SubtitleCues.toCues(cues)) }
                }
            },
            // Ayar değişince anında uygulanır; oynatma kesilmez.
            update = { view -> view.subtitleView?.let { SubtitleStyling.apply(it, subtitleStyle) } },
            onRelease = { subtitles.attach(null) },
            modifier = Modifier.fillMaxSize(),
        )

        subtitles.status.takeIf { it != SubtitleStatus.Idle }?.let { status ->
            SubtitleNotice(
                text = stringResource(if (status == SubtitleStatus.Loading) R.string.subtitle_loading else R.string.subtitle_load_failed),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            )
        }

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
                barFocus = barFocus,
                onBarFocusChange = { barFocused = it },
                noticeFocus = if (resumeNotice) noticeFocus else null,
                onBack = onBack,
                onTogglePlay = { controller.togglePlay(); bump() },
                onSeek = ::seek,
                onSeekTo = { controller.seekTo(it); position = it; bump() },
                onSubtitles = { showSubtitles = true },
                onSources = { showSources = true },
                onInteract = ::bump,
            )
        }

        if (resumeNotice && ui.error == null) {
            ResumeNotice(
                onRestart = { controller.seekTo(0L); position = 0L; resumeNotice = false; bump() },
                focus = noticeFocus,
                upFocus = playFocus,
                onFocusChange = { noticeFocused = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
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

    if (showMenu) {
        PlayerMenu(
            onSubtitles = { showMenu = false; showSubtitles = true },
            onSources = { showMenu = false; showSources = true },
            onDismiss = { showMenu = false },
        )
    }

    if (showSubtitles) {
        SubtitleMenu(
            subtitles = subtitles,
            onAppearance = { showSubtitles = false; showAppearance = true },
            onSync = { showSubtitles = false; showSync = true },
            onDismiss = { showSubtitles = false },
        )
    }

    // Senkron paneli videonun üstünde durur ve karartmaz: altyazı değişirken satırlar görünmeye devam eder.
    if (showSync) SubtitleSyncPanel(subtitles = subtitles, onDismiss = { showSync = false })

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
            onPicked = { showSources = false; resumeNotice = false; onSourcePicked(it) },
        )
    }
}

/** Altyazı yükleniyor / yüklenemedi: tek satırlık, sade bildirim. */
@Composable
private fun SubtitleNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** Sade, kısa ömürlü bildirim: konum korunur, tek düğme baştan başlatır. */
@Composable
private fun ResumeNotice(
    onRestart: () -> Unit,
    focus: FocusRequester,
    upFocus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.player_resuming),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(end = 4.dp),
        )
        // Kumanda: oynat düğmesinden Aşağı ile gelinir (Controls), Yukarı ile oynat düğmesine dönülür.
        TextButton(
            onClick = onRestart,
            modifier = Modifier
                .focusRequester(focus)
                .focusProperties { up = upFocus }
                .onFocusChanged { onFocusChange(it.hasFocus) },
        ) {
            Text(stringResource(R.string.resume_restart))
        }
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
    barFocus: FocusRequester,
    onBarFocusChange: (Boolean) -> Unit,
    noticeFocus: FocusRequester?,
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
                val upToBar = Modifier.focusProperties { up = barFocus }
                SeekButton(PlayIcons.SeekBack, stringResource(R.string.player_rewind), tint, modifier = upToBar) { onSeek(-10_000L) }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .focusRequester(playFocus)
                        .focusProperties {
                            up = barFocus
                            down = noticeFocus ?: FocusRequester.Default
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) PlayIcons.Pause else PlayIcons.PlayTriangle,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                SeekButton(PlayIcons.SeekForward, stringResource(R.string.player_forward), tint, iconFirst = false, modifier = upToBar) { onSeek(10_000L) }
            }

            ProgressBar(
                position = position,
                duration = duration,
                onSeekTo = onSeekTo,
                onInteract = onInteract,
                focus = barFocus,
                downFocus = playFocus,
                onFocusChange = onBarFocusChange,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(width = 72.dp, height = 56.dp)) {
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
    focus: FocusRequester,
    downFocus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragFraction else fraction

    Row(
        modifier = modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.hasFocus) }.focusRing(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatTime((shown * duration).toLong()), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragFraction = it; onInteract() },
            onValueChangeFinished = {
                if (duration > 0) onSeekTo((dragFraction * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .focusRequester(focus)
                .focusProperties { down = downFocus },
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

/** Kumandada Aşağı ile açılan küçük menü: altyazı ya da kaynak değiştir. İlk satır başlangıç odağındadır. */
@Composable
private fun PlayerMenu(onSubtitles: () -> Unit, onSources: () -> Unit, onDismiss: () -> Unit) {
    val focus = rememberInitialFocus()
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                MenuRow(PlayIcons.Subtitles, stringResource(R.string.player_subtitles), onSubtitles, Modifier.focusRequester(focus))
                MenuRow(PlayIcons.SwitchSource, stringResource(R.string.player_sources), onSources)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
    }
}
