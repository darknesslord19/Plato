package com.kaan.play.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Alt menü için çizgi stilinde, kendi çizdiğimiz sade ikonlar (24dp tuval, 1.8 kalınlık). */
object PlayIcons {

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = d,
        )

    val Home: ImageVector by lazy {
        icon("Home") {
            line {
                moveTo(4f, 11f); lineTo(12f, 4.5f); lineTo(20f, 11f)
                moveTo(6f, 9.5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(9.5f)
                moveTo(10f, 19f); verticalLineTo(14f); horizontalLineTo(14f); verticalLineTo(19f)
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            line {
                moveTo(10.5f, 4.5f)
                arcToRelative(6f, 6f, 0f, true, true, 0f, 12f)
                arcToRelative(6f, 6f, 0f, true, true, 0f, -12f)
                moveTo(15f, 15f); lineTo(19.5f, 19.5f)
            }
        }
    }

    /** İstif halinde iki kart: kütüphane. */
    val Library: ImageVector by lazy {
        icon("Library") {
            line {
                moveTo(7f, 6f); horizontalLineTo(17f)
                moveTo(5f, 9.5f); horizontalLineTo(19f)
                moveTo(5f, 12f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                horizontalLineTo(17.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(18f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(6.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                close()
            }
        }
    }

    private fun ImageVector.Builder.solid(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = d)

    private fun androidx.compose.ui.graphics.vector.PathBuilder.heart() {
        moveTo(12f, 20f)
        curveTo(5.5f, 15f, 3f, 11.5f, 3f, 8.5f)
        curveTo(3f, 5.5f, 5.2f, 4f, 7.3f, 4f)
        curveTo(9.2f, 4f, 11f, 5.2f, 12f, 6.8f)
        curveTo(13f, 5.2f, 14.8f, 4f, 16.7f, 4f)
        curveTo(18.8f, 4f, 21f, 5.5f, 21f, 8.5f)
        curveTo(21f, 11.5f, 18.5f, 15f, 12f, 20f)
        close()
    }

    val Heart: ImageVector by lazy { icon("Heart") { line { heart() } } }

    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { solid { heart() } } }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { line { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    /** Yuvarlatılmış oynat üçgeni (launcher işaretiyle aynı biçim). */
    val PlayTriangle: ImageVector by lazy {
        icon("PlayTriangle") {
            solid {
                moveTo(8f, 5.5f)
                curveTo(8f, 4.4f, 8.9f, 3.9f, 9.8f, 4.5f)
                lineTo(19f, 10.6f)
                curveTo(19.9f, 11.2f, 19.9f, 12.8f, 19f, 13.4f)
                lineTo(9.8f, 19.5f)
                curveTo(8.9f, 20.1f, 8f, 19.6f, 8f, 18.5f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") { line { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    }

    val Back: ImageVector by lazy {
        icon("Back") {
            line {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            line {
                moveTo(12f, 5f); verticalLineTo(19f)
                moveTo(5f, 12f); horizontalLineTo(19f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            line {
                moveTo(4.5f, 7f); horizontalLineTo(19.5f)
                moveTo(9.5f, 7f); verticalLineTo(4.5f); horizontalLineTo(14.5f); verticalLineTo(7f)
                moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); horizontalLineTo(16.5f); lineTo(17.5f, 7f)
                moveTo(10f, 11f); verticalLineTo(16f)
                moveTo(14f, 11f); verticalLineTo(16f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            line {
                moveTo(5f, 7f); horizontalLineTo(19f)
                moveTo(5f, 12f); horizontalLineTo(19f)
                moveTo(5f, 17f); horizontalLineTo(19f)
                moveTo(9f, 5f); verticalLineTo(9f)
                moveTo(15f, 10f); verticalLineTo(14f)
                moveTo(8f, 15f); verticalLineTo(19f)
            }
        }
    }
}
