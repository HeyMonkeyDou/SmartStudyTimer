package com.group10.smartstudytimer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statisticsRepository.syncLocalSessionsFromFirebase(
            onSuccess = { setupNavigation() },
            onError = { setupNavigation() }
        )
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Default page
        loadFragment(Home())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {

                R.id.home -> {
                    loadFragment(Home())
                    true
                }

                R.id.statistics -> {
                    Toast.makeText(this, "Under development, coming soon", Toast.LENGTH_SHORT).show()
                    false
                }

                R.id.monitor -> {
                    loadFragment(Monitor())
                    true
                }

                R.id.profile -> {
                    Toast.makeText(this, "Under development, coming soon", Toast.LENGTH_SHORT).show()
                    false
                }

                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
