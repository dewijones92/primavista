package com.dewijones92.primavista

import android.app.Application
import com.dewijones92.primavista.di.AppContainer

public class PrimaVistaApp : Application() {

    public lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.diag.event(
            "app",
            "started build=${BuildConfig.GIT_SHA} version=${BuildConfig.VERSION_NAME} " +
                "code=${BuildConfig.VERSION_CODE}",
        )
    }
}
