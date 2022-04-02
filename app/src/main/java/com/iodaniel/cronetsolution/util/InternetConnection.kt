package com.iodaniel.cronetsolution.util

import android.content.Context
import android.net.ConnectivityManager

class InternetConnection(val context: Context) {

    interface CheckInternetConnection {
        fun isConnected()
        fun notConnected()
    }

    lateinit var internetConnectionListener: CheckInternetConnection

    fun setCustomInternetListener(internetConnectionListener: CheckInternetConnection) {
        this.internetConnectionListener = internetConnectionListener
        isConnectedToInternet()
    }

    private fun isConnectedToInternet() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        val connected = activeNetwork?.isConnectedOrConnecting == true
        if (connected) {
            internetConnectionListener.isConnected()
        } else {
            internetConnectionListener.notConnected()
        }
    }
}