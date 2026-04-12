package com.group10.smartstudytimer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import android.view.View

class MainActivity : AppCompatActivity() {

    private val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || currentUser.isAnonymous) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

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
                    loadFragment(Statistic())
                    true
                }

                R.id.monitor -> {
                    loadFragment(Monitor())
                    true
                }

                R.id.friends -> {
                    loadFragment(Friends())
                    true
                }

                R.id.profile -> {
                    loadFragment(Profile())
                    true
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

    fun setBottomNavVisibility(visible: Boolean) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.visibility = if (visible) View.VISIBLE else View.GONE
    }
}