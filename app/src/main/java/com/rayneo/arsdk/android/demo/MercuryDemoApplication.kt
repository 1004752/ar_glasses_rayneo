package com.rayneo.arsdk.android.demo

import android.app.Application
import com.rayneo.arsdk.android.MercurySDK


class MercuryDemoApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        MercurySDK.init(this)
    }
}