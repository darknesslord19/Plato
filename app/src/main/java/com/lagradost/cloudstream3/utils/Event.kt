package com.lagradost.cloudstream3.utils

/*
 * CloudStream'in olay sınıfları (ikili uyumlu). Eklentiler `MainActivity.afterPluginsLoadedEvent += { ... }` gibi
 * dinleyiciler ekler. Kaynak: recloudstream/cloudstream (GPL-3.0), app/.../utils/Event.kt.
 */

class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: (T) -> Unit) {
        synchronized(observers) { observers.add(observer) }
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        synchronized(observers) { observers.remove(observer) }
    }

    operator fun invoke(value: T) {
        val snapshot = synchronized(observers) { observers.toList() }
        for (observer in snapshot) {
            try {
                observer(value)
            } catch (_: Throwable) {
                // Bir eklentinin dinleyicisi hata verirse diğerleri ve uygulama etkilenmez.
            }
        }
    }
}

class EmptyEvent {
    private val observers = mutableSetOf<Runnable>()

    val size: Int get() = observers.size

    operator fun plusAssign(observer: Runnable) {
        synchronized(observers) { observers.add(observer) }
    }

    operator fun minusAssign(observer: Runnable) {
        synchronized(observers) { observers.remove(observer) }
    }

    operator fun invoke() {
        val snapshot = synchronized(observers) { observers.toList() }
        for (observer in snapshot) {
            try {
                observer.run()
            } catch (_: Throwable) {
            }
        }
    }
}
