package com.kaan.play.data.plugin.runtime

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Eklentinin `onSettings()` ile bildirdiği ayar düzeninin öğeleri (docs/nuvio-plugin-format.md §2.4): `header`, `info`,
 * `toggle`, `select`, `text` (+`isPassword`). Tanınmayan türler ve anahtarsız değer öğeleri atılır. Kullanıcı değerleri
 * `key` ile saklanır; `toggle` bool, `select`/`text` metindir.
 */
sealed interface PluginSettingField {
    data class Header(val label: String) : PluginSettingField
    data class Info(val label: String) : PluginSettingField
    data class Toggle(val key: String, val label: String, val default: Boolean, val description: String?) : PluginSettingField
    data class Select(
        val key: String,
        val label: String,
        val default: String,
        val options: List<Option>,
        val description: String?,
    ) : PluginSettingField
    data class Text(
        val key: String,
        val label: String?,
        val placeholder: String,
        val isPassword: Boolean,
        val description: String?,
    ) : PluginSettingField

    data class Option(val value: String, val label: String)

    companion object {
        private const val MAX_FIELDS = 60
        private const val MAX_OPTIONS = 40

        /** `{"ok":true,"layout":[...]}` biçimindeki JS çıktısını ayrıştırır; bozuksa boş liste. */
        fun parseLayout(json: String): List<PluginSettingField> {
            val root = try {
                PlayJson.parseToJsonElement(json) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            val layout = root["layout"] as? JsonArray ?: return emptyList()
            return layout.asSequence().mapNotNull { (it as? JsonObject)?.let(::field) }.take(MAX_FIELDS).toList()
        }

        private fun field(o: JsonObject): PluginSettingField? {
            fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.let(PluginStream::clean)
            val key = (o["key"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }
            val description = str("description")
            return when ((o["type"] as? JsonPrimitive)?.contentOrNull) {
                "header" -> str("label")?.let(::Header)
                "info" -> str("label")?.let(::Info)
                "toggle" -> key?.let {
                    Toggle(it, str("label") ?: it, (o["defaultValue"] as? JsonPrimitive)?.contentOrNull == "true", description)
                }
                "select" -> key?.let {
                    val options = (o["options"] as? JsonArray).orEmpty().asSequence().mapNotNull { opt ->
                        val obj = opt as? JsonObject ?: return@mapNotNull null
                        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        Option(value, (obj["label"] as? JsonPrimitive)?.contentOrNull?.let(PluginStream::clean) ?: value)
                    }.take(MAX_OPTIONS).toList()
                    if (options.isEmpty()) null
                    else Select(it, str("label") ?: it, (o["defaultValue"] as? JsonPrimitive)?.contentOrNull ?: options.first().value, options, description)
                }
                "text" -> key?.let {
                    Text(it, str("label"), str("placeholder").orEmpty(), (o["isPassword"] as? JsonPrimitive)?.contentOrNull == "true", description)
                }
                else -> null
            }
        }
    }
}
