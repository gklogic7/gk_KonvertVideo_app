package com.example

import android.app.Application
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.example.data.local.KonvertDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.ConversionRepository
import com.example.player.KvPlayerController

class KonvertApplication : Application() {
    lateinit var repository: ConversionRepository
        private set
    lateinit var kvPlayerController: KvPlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching {
            FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.NEVER_PRINT_LOGS)
        }
        val database = KonvertDatabase.getInstance(this)
        val settingsDataStore = SettingsDataStore(this)
        repository = ConversionRepository(
            appContext = this,
            historyDao = database.historyDao(),
            customPresetDao = database.customPresetDao(),
            settingsDataStore = settingsDataStore
        )
        kvPlayerController = KvPlayerController(this)
    }
}
