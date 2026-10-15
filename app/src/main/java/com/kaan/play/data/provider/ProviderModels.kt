package com.kaan.play.data.provider

enum class ProviderTab { All, CloudStream, Nuvio }

enum class ProviderKind { CloudStream, NuvioAddon, NuvioPlugin }

/**
 * Sağlayıcı seçicide listelenen tek bir eklenti / sağlayıcı öğesi.
 * [id]: Benzersiz kimlik (örn. "cs:12", "nuvio_addon:3", "nuvio_plugin:5").
 * [name]: Eklentinin adı (örn. "DiziBox", "VidMoly").
 * [subtitle]: "CloudStream · repo adı" veya "Nuvio · addon adı".
 */
data class ProviderItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val kind: ProviderKind,
    val isPinned: Boolean = false,
)

/**
 * B aşamasında (katalog çekme) eklentinin kendi ana sayfa verilerini sağlaması için ince arayüz.
 * CloudStream `getMainPage` veya Nuvio katalog akışı bu arayüze bağlanacak.
 */
interface ProviderCatalog {
    val providerId: String
    val name: String
}

object ProviderConstants {
    /** "Tüm sağlayıcılar" (varsayılan TMDB akışı) özel kimliği. */
    const val ALL_PROVIDERS_ID = "__all__"
}
