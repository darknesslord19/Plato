package com.kaan.play.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons

/** Alt ekranların ortak başlığı: geri oku + başlık. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}
