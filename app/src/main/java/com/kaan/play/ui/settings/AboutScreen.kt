package com.kaan.play.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.play.R
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing

/**
 * Hakkında ekranı: Referans tasarıma uygun Telegram Topluluğu kartı.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.about_telegram_url)
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Geri düğmesi ve "Hakkında" başlığı
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color(0xFF0F1E2E),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.6f)),
                modifier = Modifier
                    .size(40.dp)
                    .focusRing(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PlayIcons.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Telegram Topluluk Kartı
        Surface(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)))
            },
            shape = cardShape,
            color = Color(0xFF062A3B).copy(alpha = 0.85f),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .focusRing(cardShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Işıltılı Telegram dairesel ikonu
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.28f), Color.Transparent),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00A9E0),
                        border = BorderStroke(2.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = PlayIcons.TelegramPlane,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_telegram_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_telegram_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = Color(0xFFA0C0D8),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Sağ dairesel ok butonu
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF071F2C),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PlayIcons.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
