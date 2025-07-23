package com.serifpersia.heartmonitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)

    fun getAge(): Int {
        return prefs.getInt("user_age", 35)
    }

    fun setAge(age: Int) {
        prefs.edit { putInt("user_age", age) }
    }
}
