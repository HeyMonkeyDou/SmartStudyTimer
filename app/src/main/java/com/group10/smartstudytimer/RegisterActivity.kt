package com.group10.smartstudytimer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {

    private val authRepository by lazy { AuthRepository(this) }
    private val firebaseRepository by lazy { FirebaseRepository() }

    private lateinit var usernameField: TextInputEditText
    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var confirmPasswordField: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var registerButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        usernameField = findViewById(R.id.registerUsername)
        emailField = findViewById(R.id.registerEmail)
        passwordField = findViewById(R.id.registerPassword)
        confirmPasswordField = findViewById(R.id.registerConfirmPassword)
        statusText = findViewById(R.id.registerStatusText)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener { attemptRegister() }

        findViewById<TextView>(R.id.goToLoginLink).setOnClickListener {
            startActivity(Intent(this, EmailLoginActivity::class.java))
            finish()
        }
    }

    private fun attemptRegister() {
        val username = usernameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        val confirmPassword = confirmPasswordField.text.toString()

        if (!validateInputs(username, email, password, confirmPassword)) return

        setLoadingState(true)

        // Step 1: check username uniqueness
        firebaseRepository.isUsernameAvailable(
            username = username,
            onResult = { available ->
                if (!available) {
                    showError(getString(R.string.username_taken))
                    setLoadingState(false)
                    return@isUsernameAvailable
                }
                // Step 2: create Firebase Auth user
                authRepository.createUserWithEmailAndPassword(
                    email = email,
                    password = password,
                    onSuccess = {
                        // Step 3: save profile to Firestore with chosen username
                        firebaseRepository.saveCurrentUserProfile(
                            displayName = username,
                            totalFocusMinutes = 0,
                            avatarId = "",
                            username = username,
                            onSuccess = { openMainScreen() },
                            onError = { error ->
                                showError(error.message ?: getString(R.string.register_failed))
                                setLoadingState(false)
                            }
                        )
                    },
                    onError = { error ->
                        showError(error.message ?: getString(R.string.register_failed))
                        setLoadingState(false)
                    }
                )
            },
            onError = { error ->
                showError(error.message ?: getString(R.string.register_failed))
                setLoadingState(false)
            }
        )
    }

    private fun validateInputs(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (username.isBlank()) {
            showError(getString(R.string.username_required))
            return false
        }
        if (username.length < 3) {
            showError(getString(R.string.username_too_short))
            return false
        }
        if (!username.matches(Regex("[a-zA-Z0-9_]+"))) {
            showError(getString(R.string.username_invalid_chars))
            return false
        }
        if (email.isBlank()) {
            showError(getString(R.string.email_required))
            return false
        }
        if (password.length < 6) {
            showError(getString(R.string.password_too_short))
            return false
        }
        if (password != confirmPassword) {
            showError(getString(R.string.passwords_do_not_match))
            return false
        }
        return true
    }

    private fun showError(message: String) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }

    private fun setLoadingState(loading: Boolean) {
        registerButton.isEnabled = !loading
        usernameField.isEnabled = !loading
        emailField.isEnabled = !loading
        passwordField.isEnabled = !loading
        confirmPasswordField.isEnabled = !loading
        if (loading) statusText.visibility = View.GONE
    }

    private fun openMainScreen() {
        Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
