package com.lagradost.cloudstream3

import com.kaan.play.data.cloudstream.PlayNetworkGuard as InternalPlayNetworkGuard

/**
 * ABI ve import geriye dönük uyumluluğu için köprü nesne.
 */
object PlayNetworkGuard {
    fun checkUrl(url: String) = InternalPlayNetworkGuard.checkUrl(url)
    fun allowLoopbackPort(port: Int) = InternalPlayNetworkGuard.allowLoopbackPort(port)
    fun disallowLoopbackPort(port: Int) = InternalPlayNetworkGuard.disallowLoopbackPort(port)
}
