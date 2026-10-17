package com.kaan.play.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.ui.components.ScreenHeader

/** Hakkında: uygulama adı, sürüm, Telegram bağlantısı, "içerik sağlamaz" notu ve TMDB'nin zorunlu atıf metni. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.settings_about), onBack = onBack)
        // Tam logo üstte ortalı. Logonun yazısı açık renkli olduğu için açık temada da okunsun diye koyu bir zemin üstünde durur.
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth(0.7f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colorResource(R.color.play_background))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Telegram bağlantısı
            val telegramUrl = stringResource(R.string.about_telegram_url)
            Text(
                text = stringResource(R.string.about_telegram) + "  →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
                    }
                    .padding(vertical = 4.dp),
            )
            Text(
                stringResource(R.string.about_no_content),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.about_web_blocking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.about_tmdb_attribution),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.about_license_attribution),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

}
