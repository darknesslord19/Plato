package com.kaan.play

import android.app.Application

class PlayApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // İndirme yöneticisi açılışta kurulur: yarım kalan indirmeler (uygulama öldürüldükten sonra) kendiliğinden devam eder.
        container.downloads
    }
}
