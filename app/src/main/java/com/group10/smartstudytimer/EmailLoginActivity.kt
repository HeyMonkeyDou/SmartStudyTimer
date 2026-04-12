package com.group10.smartstudytimer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class EmailLoginActivity : AppCompatActivity() {

    private val authRepository by lazy { AuthRepository(this) }
    private val firebaseRepository by lazy { FirebaseRepository() }

    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var loginButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_login)

        emailField = findViewById(R.id.loginEmail)
        passwordField = findViewById(R.id.loginPassword)
        statusText = findViewById(R.id.loginStatusText)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener { attemptLogin() }

        findViewById<TextView>(R.id.goToRegisterLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun attemptLogin() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showError(getString(R.string.fill_all_fields))
            return
        }

        setLoadingState(true)

        authRepository.signInWithEmailAndPassword(
            email = email,
            password = password,
            onSuccess = { openMainScreen() },
            onError = { error ->
                showError(error.message ?: getString(R.string.login_failed))
                setLoadingState(false)
            }
        )
    }

    private fun showError(message: String) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }

    private fun setLoadingState(loading: Boolean) {
        loginButton.isEnabled = !loading
        emailField.isEnabled = !loading
        passwordField.isEnabled = !loading
        if (loading) statusText.visibility = View.GONE
    }

    private fun openMainScreen() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
