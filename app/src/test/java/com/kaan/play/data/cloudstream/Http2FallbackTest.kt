package com.kaan.play.data.cloudstream

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.junit.Ignore
import org.junit.Test
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Http2FallbackTest {
    @Ignore("Canlı ağ testi")
    @Test
    fun testPhotourHttp1VsHttp2() {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }

        val url = "https://vs6.photour.org/v/D.I.S.C.O.2026.WEB-DL.1080p.H.264-HDM/master.m3u8"
        val http1Client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://vidrame.live/")
            .build()

        try {
            val resp = http1Client.newCall(req).execute()
            println("HTTP/1.1 SUCCESS: code=${resp.code} protocol=${resp.protocol}")
            val body = resp.body?.string().orEmpty()
            println("HTTP/1.1 BODY: ${body.take(200)}")
            resp.close()
        } catch (e: Exception) {
            println("HTTP/1.1 FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }

        val http2Client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()

        try {
            val resp = http2Client.newCall(req).execute()
            println("HTTP/2 SUCCESS: code=${resp.code} protocol=${resp.protocol}")
            val body = resp.body?.string().orEmpty()
            println("HTTP/2 BODY: ${body.take(200)}")
            resp.close()
        } catch (e: Exception) {
            println("HTTP/2 FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
