package com.kaan.play.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.data.tmdb.TmdbException.Kind

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Anlaşılır tek cümle + "Tekrar dene". Sunucu metni ya da URL gösterilmez. */
@Composable
fun ErrorMessage(error: TmdbException, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.userMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
fun TmdbException.userMessage(): String = when (kind) {
    Kind.MissingApiKey -> stringResource(R.string.tmdb_error_missing_key)
    Kind.InvalidApiKey -> stringResource(R.string.tmdb_error_invalid_key)
    Kind.Network -> stringResource(R.string.tmdb_error_network)
    Kind.RateLimited -> stringResource(R.string.tmdb_error_rate_limited)
    Kind.NotFound -> stringResource(R.string.tmdb_error_not_found)
    Kind.Http, Kind.Parse -> stringResource(R.string.tmdb_error_generic)
}
