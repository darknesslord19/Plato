package com.kaan.play.ui.addons

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaan.play.R
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind

/** Hata türünden yerelleştirilmiş, anlaşılır mesaj. Sunucudan gelen metin ya da URL asla gösterilmez. */
@Composable
fun AddonException.userMessage(): String = when (kind) {
    Kind.InvalidUrl -> stringResource(R.string.addon_error_invalid_url)
    Kind.AlreadyAdded -> stringResource(R.string.addon_error_already_added)
    Kind.UnsupportedKind -> stringResource(R.string.addon_error_unsupported_kind)
    Kind.Network -> stringResource(R.string.addon_error_network)
    Kind.Http -> stringResource(R.string.addon_error_http, detail.orEmpty())
    Kind.TooLarge -> stringResource(R.string.addon_error_too_large)
    Kind.NotJson -> stringResource(R.string.addon_error_not_json)
    Kind.MissingField -> stringResource(R.string.addon_error_missing_field, detail.orEmpty())
    Kind.InvalidField -> stringResource(R.string.addon_error_invalid_field, detail.orEmpty())
    Kind.NoStreamResource -> stringResource(R.string.addon_error_no_stream)
    Kind.UnsupportedTypes -> stringResource(R.string.addon_error_unsupported_types)
}
