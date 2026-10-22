package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event

/**
 * CloudStream'in ana etkinliğinin yalnızca eklentilerin kullandığı yardımcı nesnesi (ikili uyumlu).
 * Eklentiler bu olaylara dinleyici ekler; Play bir eklenti yüklendiğinde [afterPluginsLoadedEvent]'i tetikler.
 * Play'in gerçek etkinliği `com.kaan.play.MainActivity`'dir; bu sınıf bir etkinlik değildir.
 */
class MainActivity private constructor() {
    companion object {
        const val TAG = "MAINACT"
        const val ANIMATED_OUTLINE: Boolean = false

        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent = Event<Boolean>()
        val afterRepositoryLoadedEvent = Event<Boolean>()
        val bookmarksUpdatedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
        val reloadLibraryEvent = Event<Boolean>()
        val reloadAccountEvent = Event<Boolean>()
    }
}
