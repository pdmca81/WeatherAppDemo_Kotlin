package com.pradeep.weatherappdemo.service

import android.content.Context
import com.google.gson.GsonBuilder
import com.pradeep.weatherappdemo.api.WeatherAPI
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkService private constructor(context: Context) {
    val weatherAPI: WeatherAPI
    var retrofit: Retrofit

    companion object {
        private var INSTANCE: NetworkService? = null
        @JvmStatic
        fun getInstance(context: Context): NetworkService? {
            if (INSTANCE == null) {
                INSTANCE = NetworkService(context)
            }
            return INSTANCE
        }
    }

    init {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        val gson = GsonBuilder()
                .setLenient()
                .create()
        retrofit = Retrofit.Builder()
                .baseUrl(WeatherAPI.BASE_URL)
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        weatherAPI = retrofit.create(WeatherAPI::class.java)
    }
}