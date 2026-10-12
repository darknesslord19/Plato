package com.kaan.play.data.web

/**
 * Web görünümünde engellenen reklam/izleyici alan adları. Kendi derlediğimiz küçük bir listedir (başkasının
 * lisanslı filtre listesi değildir); her reklamı yakalamaz. Eklemek için alana tek satır yazmak yeter:
 * bir alan adı kendisini ve tüm alt alan adlarını kapsar (`doubleclick.net` -> `ads.doubleclick.net`).
 * Yaygın sitelerin çalışması için gerekli olabilecek alan adları (CDN, oynatıcı, yorum vb.) buraya konmaz.
 */
object AdDomains {

    val all: List<String> = listOf(
        // Büyük reklam ağları ve ihale (RTB) uçları
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "fundingchoicesmessages.google.com",
        "2mdn.net",
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "indexww.com",
        "smartadserver.com",
        "adform.net",
        "serving-sys.com",
        "advertising.com",
        "media.net",
        "yieldmo.com",
        "teads.tv",
        "sharethrough.com",
        "triplelift.com",
        "33across.com",
        "bidswitch.net",
        "lijit.com",
        "sovrn.com",
        "adroll.com",
        "ads.yahoo.com",
        "ads-twitter.com",
        "adthrive.com",
        "mediavine.com",
        "buysellads.com",
        "carbonads.net",

        // Öneri/yönlendirme kutuları
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "mgid.com",

        // Açılır pencere / yönlendirme / itme bildirimi ağları
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "onclickads.net",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "trafficjunky.net",
        "adsterra.com",
        "highperformanceformat.com",
        "profitablegatecpm.com",
        "effectivegatecpm.com",
        "hilltopads.net",
        "clickadu.com",
        "adcash.com",
        "a-ads.com",
        "ad-maven.com",
        "admaven.com",
        "bidvertiser.com",
        "zedo.com",
        "monetag.com",
        "galaksion.com",
        "realsrv.com",
        "tsyndicate.com",
        "plugrush.com",

        // Ölçüm, doğrulama ve izleyiciler
        "moatads.com",
        "adsafeprotected.com",
        "doubleverify.com",
        "scorecardresearch.com",
        "quantserve.com",
        "krxd.net",
        "bluekai.com",
        "demdex.net",
        "rlcdn.com",
        "everesttech.net",
        "google-analytics.com",
        "hotjar.com",
        "mc.yandex.ru",

        // Mobil reklam SDK'larının web uçları
        "smaato.net",
        "inmobi.com",
        "mopub.com",
        "applovin.com",
        "unityads.unity3d.com",
    )
}
