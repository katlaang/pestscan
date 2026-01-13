package com.pestscan.mobile.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pestscan.mobile.R

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.pestscan.mobile.ui.farm.FarmFragment())
                .commit()
        }
    }
}
