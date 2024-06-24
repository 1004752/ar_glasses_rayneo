package com.sk.csplayer

import android.app.Application
import android.content.Context
import com.rayneo.arsdk.android.MercurySDK
import com.sk.csplayer.util.Preferences.init

class CsPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        init(this)

        appContext = this

        MercurySDK.init(this)
    }

    companion object {
        var appContext: Context? = null
            private set
    }
}