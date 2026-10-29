package com.kaan.play.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.schedule.CalendarEntry
import com.kaan.play.data.schedule.Days
import com.kaan.play.data.schedule.NewEpisodeWorker
import com.kaan.play.data.schedule.ScheduleRules
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private sealed interface CalendarState {
    data object Loading : CalendarState
    data class Ready(val recent: List<CalendarEntry>, val upcoming: List<CalendarEntry>, val followed: Int) : CalendarState
}

/**
 * Dizi takvimi: takip edilen dizilerin (favoriler + izleme listesi) son 7 günde yayınlanan ve önümüzdeki 60 günde
 * yayınlanacak bölümleri. Üstte "Yeni bölüm bildirimleri" anahtarı (Android 13+ açarken bildirim izni istenir).
 */
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenDetail: (MediaType, Int) -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CalendarState>(CalendarState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val alerts by container.settings.episodeAlerts.collectAsStateWithLifecycle(initialValue = true)
    var canNotify by remember { mutableStateOf(NewEpisodeWorker.canNotify(context)) }
    val focus = rememberFocusMemory(enabled = state is CalendarState.Ready)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && NewEpisodeWorker.canNotify(context)
    }

    LaunchedEffect(reload) {
        state = CalendarState.Loading
        val shows = runCatching { container.episodeSchedule.load(force = reload > 0) }.getOrDefault(emptyList())
        val followed = runCatching { container.episodeSchedule.followedShowIds().size }.getOrDefault(0)
        val (recent, upcoming) = ScheduleRules.calendar(shows, Days.today())
        state = CalendarState.Ready(recent, upcoming, followed)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.calendar_title),
            onBack = onBack,
            action = {
                TextButton(onClick = { reload++ }, enabled = state is CalendarState.Ready) {
                    Text(stringResource(R.string.calendar_refresh))
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "alerts") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusMemory(focus, "alerts", isDefault = true)
                            .toggleable(value = alerts, role = Role.Switch) { on ->
                                scope.launch { container.settings.setEpisodeAlerts(on) }
                                if (on && !canNotify && Build.VERSION.SDK_INT >= 33) {
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.calendar_alerts), style = MaterialTheme.typography.titleMedium)
                            if (alerts && !canNotify) {
                                Text(
                                    stringResource(R.string.calendar_alerts_no_permission),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Switch(
                            checked = alerts,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            when (val s = state) {
                CalendarState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is CalendarState.Ready -> {
                    if (s.followed == 0 || (s.recent.isEmpty() && s.upcoming.isEmpty())) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(if (s.followed == 0) R.string.calendar_empty_none else R.string.calendar_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    if (s.upcoming.isNotEmpty()) {
                        item(key = "h-up") { Header(stringResource(R.string.calendar_upcoming)) }
                        items(s.upcoming, key = { "u-" + it.episode.keyFor(it.show.tvId) }) { e ->
                            EntryRow(e, Modifier.focusMemory(focus, "u-" + e.episode.keyFor(e.show.tvId))) { onOpenDetail(MediaType.Tv, e.show.tvId) }
                        }
                    }
                    if (s.recent.isNotEmpty()) {
                        item(key = "h-recent") { Header(stringResource(R.string.calendar_recent)) }
                        items(s.recent, key = { "r-" + it.episode.keyFor(it.show.tvId) }) { e ->
                            EntryRow(e, Modifier.focusMemory(focus, "r-" + e.episode.keyFor(e.show.tvId))) { onOpenDetail(MediaType.Tv, e.show.tvId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun EntryRow(entry: CalendarEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusRing(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = TmdbImages.url(entry.show.posterPath, TmdbImages.Size.Poster),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(52.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.show.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val ep = entry.episode
                Text(
                    text = if (ep.name.isBlank()) {
                        stringResource(R.string.calendar_episode, ep.season, ep.episode)
                    } else {
                        stringResource(R.string.calendar_episode_named, ep.season, ep.episode, ep.name)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = dayLabel(entry.day),
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.aired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** "Bugün" / "Yarın" / "Dün" ya da kısa tarih ("31 Eki Cum"), arayüz dilinde. */
@Composable
private fun dayLabel(day: Long): String {
    val today = Days.today()
    return when (day - today) {
        0L -> stringResource(R.string.calendar_today)
        1L -> stringResource(R.string.calendar_tomorrow)
        -1L -> stringResource(R.string.calendar_yesterday)
        else -> {
            val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
            SimpleDateFormat("d MMM EEE", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(day * 86_400_000L))
        }
    }
}
