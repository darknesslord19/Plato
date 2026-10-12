package com.kaan.play.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages

/**
 * Play'in kart biçimi: ince çerçeveli 2:3 afiş (12dp köşe), altında başlık ve soluk alt satır.
 * [progress] (0..1) verilirse afişin altında ince ilerleme çizgisi çıkar ("devam et").
 * [overlay]: afişin üstüne çizilen küçük eylem (ör. favoriden çıkar düğmesi).
 */
@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = MaterialTheme.shapes.medium
    // Tüm sütunu yuvarlak kırpmayız: alt satırın ilk harfini keserdi. Yalnızca afiş kırpılır.
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            overlay()
            if (progress != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Kart için "2019 · Dizi" gibi soluk satır. Yıl yoksa yalnızca tür. */
@Composable
fun MediaSummary.cardSubtitle(): String {
    val typeLabel = stringResource(if (type == MediaType.Movie) R.string.type_movie else R.string.type_series)
    return year?.let { stringResource(R.string.dot_join, it.toString(), typeLabel) } ?: typeLabel
}

@Composable
fun MediaSummary.posterUrl(): String? = TmdbImages.url(posterPath, TmdbImages.Size.Poster)
