package com.pradeep.weatherappdemo.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.pradeep.weatherappdemo.R
import com.pradeep.weatherappdemo.api.WeatherAPI
import com.pradeep.weatherappdemo.db.DatabaseHelper
import com.pradeep.weatherappdemo.model.Weather
import com.pradeep.weatherappdemo.service.NetworkService
import com.pradeep.weatherappdemo.utils.Utils
import com.pradeep.weatherappdemo.utils.Utils.isNetworkAvailable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class WeatherActivity : AppCompatActivity(), View.OnClickListener{

    private var addressTxt: TextView? = null
    private var cityTxt: TextView? = null
    private var updatedAtTxt: TextView? = null
    private var statusTxt: TextView? = null
    private var tempTxt: TextView? = null
    private var tempMinTxt: TextView? = null
    private var tempMaxTxt: TextView? = null
    private var sunriseTxt: TextView? = null
    private var sunsetTxt: TextView? = null
    private var windTxt: TextView? = null
    private var pressureTxt: TextView? = null
    private var humidityTxt: TextView? = null
    private var retryBtn: Button? = null
    private var currentActivity: Activity? = null
    var db: SQLiteDatabase? = null
    private var dbHelper: DatabaseHelper? = null
    var retrofit: Retrofit? = null
    var apiService: WeatherAPI? = null
    var disposable: Disposable? = null
    val PERMISSION_ID = 42
    lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentActivity = this
        // Initialize View Objects
        addressTxt = findViewById(R.id.address)
        cityTxt = findViewById(R.id.city)
        updatedAtTxt = findViewById(R.id.updated_at)
        statusTxt = findViewById(R.id.status)
        tempTxt = findViewById(R.id.temp)
        tempMinTxt = findViewById(R.id.temp_min)
        tempMaxTxt = findViewById(R.id.temp_max)
        sunriseTxt = findViewById(R.id.sunrise)
        sunsetTxt = findViewById(R.id.sunset)
        windTxt = findViewById(R.id.wind)
        pressureTxt = findViewById(R.id.pressure)
        humidityTxt = findViewById(R.id.humidity)
        retryBtn = findViewById(R.id.retryButton)
        retryBtn!!.setOnClickListener(this)

        // Initialize Database & DatabaseHelperClass
        if (db == null || db != null && !db!!.isOpen) {
            dbHelper = DatabaseHelper.getInstance(currentActivity as Context)
            db = dbHelper!!.writableDatabase
        }

        // Check if internet connection is available or not
        if (!isNetworkAvailable(this)) {
            showErrorMessage("You have no internet connection")
            return
        }

        // Get current location if location service is enabled, else enable it and get current location.
        if (Utils.latitude == 0.0 && Utils.longitude == 0.0) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            getLastLocation()
        }

        // Showing the ProgressBar
        startLoader();

        // If data persists in local db from previous request fetch from local db and display
        val cursor = fetchWeatherInfoFromLocalDatabaseAndUpdateUI()
        if (cursor != null && cursor.count > 0) {
            cursor.moveToFirst()
            val address = cursor.getString(cursor.getColumnIndex("ADDRESS"))
            val city = cursor.getString(cursor.getColumnIndex("CITY"))
            val updatedAt = cursor.getString(cursor.getColumnIndex("UPDATED_AT"))
            val weatherStatus = cursor.getString(cursor.getColumnIndex("WEATHER_STATUS"))
            val temperature = cursor.getString(cursor.getColumnIndex("TEMPERATURE"))
            val minTemperature = cursor.getString(cursor.getColumnIndex("MIN_TEMPERATURE"))
            val maxTemperature = cursor.getString(cursor.getColumnIndex("MAX_TEMPERATURE"))
            val sunrise = cursor.getString(cursor.getColumnIndex("SUNRISE"))
            val sunset = cursor.getString(cursor.getColumnIndex("SUNSET"))
            val wind = cursor.getString(cursor.getColumnIndex("WIND"))
            val pressure = cursor.getString(cursor.getColumnIndex("PRESSURE"))
            val humidity = cursor.getString(cursor.getColumnIndex("HUMIDITY"))
            // Populating extracted data into our views
            addressTxt?.setText(address)
            cityTxt?.setText(city)
            updatedAtTxt?.setText(updatedAt)
            statusTxt?.setText(weatherStatus)
            tempTxt?.setText(temperature)
            tempMinTxt?.setText(minTemperature)
            tempMaxTxt?.setText(maxTemperature)
            sunriseTxt?.setText(sunrise)
            sunsetTxt?.setText(sunset)
            windTxt?.setText(wind)
            pressureTxt?.setText(pressure)
            humidityTxt?.setText(humidity)
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
            // Stopping the ProgressBar
            stopLoader();

            // First fetch data from local database if available and display then schedule a request after 30 secs to sync with latest response data.
            // Also set a time period for next request after 2 hrs
            apiService = NetworkService.getInstance(this)!!.weatherAPI
            disposable = Observable.interval(30000, 7200000,
                    TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ aLong: Long -> getWeatherEndpoint(aLong) }) { throwable: Throwable -> onError(throwable) }
        } else {
            // Schedule a request for every 2 hours to update. There should not be more than 1 request in a 2-hour period.
            apiService = NetworkService.getInstance(this)!!.weatherAPI
            disposable = Observable.interval(1000, 7200000,
                    TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ aLong: Long -> getWeatherEndpoint(aLong) }) { throwable: Throwable -> onError(throwable) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (disposable != null && disposable!!.isDisposed) {
            // Schedule a request for every 2 hours to update. There should not be more than 1 request in a 2-hour period.
            disposable = Observable.interval(1000, 7200000,
                    TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ aLong: Long -> getWeatherEndpoint(aLong) }) { throwable: Throwable -> onError(throwable) }
        }
    }

    override fun onPause() {
        super.onPause()
        if(disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // Sending HTTP request to the OpenWeatherMap API to retrieve the current local weather forecast
    private fun getWeatherEndpoint(aLong: Long) {
        startLoader();
        val latitude = java.lang.String.valueOf(Utils.latitude)
        val longitude = java.lang.String.valueOf(Utils.longitude)
        val units = "metric"
        val apikey = WeatherAPI.API_KEY
        val observable = apiService!!.getWeather(latitude, longitude, units, apikey)
        observable!!.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableObserver<Object?>() {
                    override fun onNext(value: Object) {
                        handleResults(value)
                    }

                    override fun onError(throwable: Throwable) {
                        showErrorMessage(throwable.toString())
                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun onError(throwable: Throwable) {
        showErrorMessage("OnError in Observable Timer")
    }

    // Parse Http response data and update UI
    private fun handleResults(result: Object) {
        try {
            val gson = Gson()
            val jsonTut: String = gson.toJson(result)
            val jsonObj = JSONObject(jsonTut)
            val main = jsonObj.getJSONObject("main")
            val sys = jsonObj.getJSONObject("sys")
            val wind = jsonObj.getJSONObject("wind")
            val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
            val updatedAt = jsonObj.getLong("dt")
            val updatedAtText = "Updated at: " + SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).format(Date(updatedAt * 1000))
            val temp = main.getString("temp") + "°C"
            val tempMin = "Min Temp: " + main.getString("temp_min") + "°C"
            val tempMax = "Max Temp: " + main.getString("temp_max") + "°C"
            val pressure = main.getString("pressure")
            val humidity = main.getString("humidity")
            val sunrise = sys.getLong("sunrise")
            val sunset = sys.getLong("sunset")
            val windSpeed = wind.getString("speed")
            val weatherDescription = weather.getString("description")
            var address = getCompleteAddressString(Utils.latitude, Utils.longitude)
            if (address != null && !address.isEmpty()) {
                address = "Current Location: $address"
            }
            val city = jsonObj.getString("name") + ", " + sys.getString("country")
            // Populating extracted data into our views
            addressTxt!!.text = address
            cityTxt!!.text = city
            updatedAtTxt!!.text = updatedAtText
            statusTxt!!.text = weatherDescription.toUpperCase()
            tempTxt!!.text = temp
            tempMinTxt!!.text = tempMin
            tempMaxTxt!!.text = tempMax
            val sunriseText = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunrise * 1000))
            sunriseTxt!!.text = sunriseText
            val sunsetText = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunset * 1000))
            sunsetTxt!!.text = sunsetText
            windTxt!!.text = windSpeed
            pressureTxt!!.text = pressure
            humidityTxt!!.text = humidity
            val weatherInfo = Weather(0, address, city, updatedAtText, weatherDescription.toUpperCase(), temp, tempMin, tempMax, sunriseText, sunsetText, windSpeed, pressure, humidity)
            insertWeatherInfoInLocalDatabase(weatherInfo)
            // Views populated, Hiding the loader, Showing the main design
            stopLoader();
        } catch (e: JSONException) {
            stopLoaderAndShowErrorMessage()
        }
    }

    private fun getCompleteAddressString(latitude: Double, longitude: Double): String {
        var completeAddress = ""
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null) {
                val returnedAddress = addresses[0]
                val strReturnedAddress = StringBuilder("")
                for (i in 0..returnedAddress.maxAddressLineIndex) {
                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n")
                }
                completeAddress = strReturnedAddress.toString()
            } else {
                showErrorMessage("No Address returned")
            }
        } catch (e: Exception) {
            showErrorMessage("Cannot get address")
        }
        return completeAddress
    }

    private fun startLoader(){
        findViewById<View>(R.id.loader).setVisibility(View.VISIBLE)
        findViewById<View>(R.id.mainContainer).visibility = View.GONE
        findViewById<View>(R.id.errorText).visibility = View.GONE
        findViewById<View>(R.id.retryButton).visibility = View.GONE
    }

    private fun stopLoader() {
        findViewById<View>(R.id.loader).setVisibility(View.GONE)
        findViewById<View>(R.id.mainContainer).visibility = View.VISIBLE
        findViewById<View>(R.id.errorText).visibility = View.GONE
        findViewById<View>(R.id.retryButton).visibility = View.GONE
    }

    private fun stopLoaderAndShowErrorMessage() {
        findViewById<View>(R.id.loader).visibility = View.GONE
        findViewById<View>(R.id.errorText).visibility = View.VISIBLE
        findViewById<View>(R.id.retryButton).visibility = View.VISIBLE
    }

    private fun showErrorMessage(msg : String){
        findViewById<View>(R.id.loader).setVisibility(View.GONE)
        findViewById<View>(R.id.mainContainer).visibility = View.GONE
        findViewById<View>(R.id.errorText).visibility = View.VISIBLE
        findViewById<View>(R.id.retryButton).visibility = View.VISIBLE

        val textView: TextView = findViewById(R.id.errorText) as TextView
        textView.text = msg;
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                mFusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    var location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        Utils.latitude = location.latitude
                        Utils.longitude = location.longitude
                    }
                }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            var mLastLocation: Location = locationResult.lastLocation
            Utils.latitude = mLastLocation.latitude
            Utils.longitude = mLastLocation.longitude
        }
    }

    private fun isLocationEnabled(): Boolean {
        var locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_ID
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getLastLocation()
            }
        }
    }

    // Persist the response so that it can be retrieved again without having to make a further network request
    private fun insertWeatherInfoInLocalDatabase(weather: Weather) {
        try {
            val cv = ContentValues()
            cv.put("ADDRESS", weather.address)
            cv.put("CITY", weather.city)
            cv.put("UPDATED_AT", weather.updatedAt)
            cv.put("WEATHER_STATUS", weather.weatherStatus)
            cv.put("TEMPERATURE", weather.temperature)
            cv.put("MIN_TEMPERATURE", weather.minTemperature)
            cv.put("MAX_TEMPERATURE", weather.maxTemperature)
            cv.put("SUNRISE", weather.sunrise)
            cv.put("SUNSET", weather.sunset)
            cv.put("WIND", weather.wind)
            cv.put("PRESSURE", weather.pressure)
            cv.put("HUMIDITY", weather.humidity)
            if (db != null) {
                db!!.delete("weather", null, null)
                db!!.insert("weather", null, cv)
            }
            if (dbHelper != null) {
                dbHelper!!.close()
            }
        } catch (e: Exception) {
            e.message
        }
    }

    private fun fetchWeatherInfoFromLocalDatabaseAndUpdateUI(): Cursor? {
        try {
            val query = "SELECT * FROM weather"
            dbHelper = DatabaseHelper.getInstance(currentActivity!!)
            db = dbHelper!!.writableDatabase
            val cursor = db?.rawQuery(query, null)
            if (cursor != null && cursor.count > 0) {
                if (dbHelper != null) {
                    dbHelper!!.close()
                }
                return cursor
            }
        } catch (e: Exception) {
            e.message
        }
        return null
    }

    override fun onClick(v: View?) {
        val item_id = v?.id
        when (item_id) {
            R.id.retryButton -> relaunchSameActivity()
        }
    }

    private fun relaunchSameActivity(){
        val intent = intent
        finish()
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        overridePendingTransition(0, 0)
        startActivity(intent)
    }
}

