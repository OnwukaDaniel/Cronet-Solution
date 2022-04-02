package com.iodaniel.cronetsolution

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Splash : AppCompatActivity() {
    private lateinit var userPref: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPref = getSharedPreferences(getString(R.string.USER_INFO), Context.MODE_PRIVATE)
        val welcomePage = userPref.getBoolean(getString(R.string.WELCOME_PAGE), true)
        when (welcomePage) {
            true -> {
                userPref.edit().putBoolean(getString(R.string.WELCOME_PAGE), false).apply()
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
            false -> {
                val intent = Intent(applicationContext, HomePage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }
    }
}