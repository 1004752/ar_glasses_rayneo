package com.sk.csplayer.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences

object Preferences {
    const val PREF_NAME: String = "VCS_SERVER_INFO"

    private var mApplicationContext: Context? = null

    private const val KEY_SERVER_ADDRESS = "server_address"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_RESOLUTION = "server_resolution"

    fun init(context: Context?) {
        mApplicationContext = context
    }

    private fun getPreferences(name: String): SharedPreferences? {
        if (mApplicationContext == null) {
            return null
        }

        return mApplicationContext!!.getSharedPreferences(name, Activity.MODE_PRIVATE)
    }

    private fun putString(name: String, key: String, value: String) {
        val pref = getPreferences(name) ?: return

        val editor = pref.edit()

        editor.putString(key, value)
        editor.apply()
    }

    private fun getString(name: String, key: String): String? {
        val pref = getPreferences(name) ?: return ""

        return pref.getString(key, "")
    }

    private fun putInt(name: String, key: String, value: Int) {
        val pref = getPreferences(name) ?: return

        val editor = pref.edit()

        editor.putInt(key, value)
        editor.apply()
    }

    private fun getInt(name: String, key: String): Int {
        val pref = getPreferences(name) ?: return 0

        return pref.getInt(key, 0)
    }

    fun setServerAddress(name: String, address: String) {
        putString(name, KEY_SERVER_ADDRESS, address)
    }

    fun getServerAddress(name: String): String? {
        return getString(name, KEY_SERVER_ADDRESS)
    }

    fun setServerPort(name: String, address: Int) {
        putInt(name, KEY_SERVER_PORT, address)
    }

    fun getServerPort(name: String): Int {
        return getInt(name, KEY_SERVER_PORT)
    }

    fun setResolution(name: String, resolution: Int) {
        putInt(name, KEY_SERVER_RESOLUTION, resolution)
    }

    fun getResolution(name: String): Int {
        return getInt(name, KEY_SERVER_RESOLUTION)
    }
}