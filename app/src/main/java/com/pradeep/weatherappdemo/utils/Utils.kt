package com.pradeep.weatherappdemo.utils

import android.content.Context
import android.net.ConnectivityManager

object Utils {
    @JvmField
    var latitude = 0.0
    @JvmField
    var longitude = 0.0
    @JvmStatic
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context != null) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val allNetworks = cm.activeNetworkInfo
            var response = false
            if (allNetworks == null) {
                response = false
            } else {
                if (allNetworks.type == ConnectivityManager.TYPE_WIFI) {
                    if (allNetworks.isConnected) {
                        response = true
                    }
                } else if (allNetworks.type == ConnectivityManager.TYPE_MOBILE) {
                    response = true
                }
            }
            return response
        }
        return false
    }
}