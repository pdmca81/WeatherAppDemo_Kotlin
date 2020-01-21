package com.pradeep.weatherappdemo.api

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherAPI {
    @GET("/data/2.5/weather")
    fun getWeather(@Query("lat") latitude: String?, @Query("lon") longitude: String?, @Query("units") units: String?, @Query("appid") apikey: String?): Observable<Object?>?

    companion object {
        const val BASE_URL = "https://api.openweathermap.org/"
        const val API_KEY = "8a1af5734b536e738f32c75e78db730e"
    }
}