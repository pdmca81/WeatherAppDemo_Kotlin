package com.pradeep.weatherappdemo.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper private constructor(private val mCxt: Context) : SQLiteOpenHelper(mCxt, "mydb", null, dBversion) {
    //this method will create the table
    //I have added IF NOT EXISTS to the SQL
    //so it will only create the table when the table is not already created
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS weather (_id INTEGER PRIMARY KEY AUTOINCREMENT, ADDRESS TEXT, CITY TEXT, UPDATED_AT TEXT, WEATHER_STATUS TEXT, TEMPERATURE TEXT, MIN_TEMPERATURE TEXT, MAX_TEMPERATURE TEXT, SUNRISE TEXT, SUNSET TEXT, WIND TEXT, PRESSURE TEXT, HUMIDITY TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        private var mInstance: DatabaseHelper? = null
        const val dBversion = 1
        @JvmStatic
        fun getInstance(ctx: Context): DatabaseHelper? {
            if (mInstance == null) {
                mInstance = DatabaseHelper(ctx.applicationContext)
            }
            return mInstance
        }
    }
}