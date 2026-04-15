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

        // Step 1: create Auth user first (so we're authenticated for Firestore queries)
        authRepository.createUserWithEmailAndPassword(
            email = email,
            password = password,
            onSuccess = {
                // Step 2: check username uniqueness while authenticated
                firebaseRepository.isUsernameAvailable(
                    username = username,
                    onResult = { available ->
                        if (!available) {
                            authRepository.deleteCurrentUser {
                                showError("This username is already taken. Please choose another.")
                                setLoadingState(false)
                            }
                            return@isUsernameAvailable
                        }
                        // Step 3: save profile with username
                        firebaseRepository.updateUsername(
                            username = username,
                            onSuccess = { openMainScreen() },
                            onError = { error ->
                                showError(error.message ?: "Registration failed.")
                                setLoadingState(false)
                            }
                        )
                    },
                    onError = { error ->
                        authRepository.deleteCurrentUser {
                            showError(error.message ?: "Registration failed.")
                            setLoadingState(false)
                        }
                    }
                )
            },
            onError = { error ->
                showError(error.message ?: "Registration failed.")
                setLoadingState(false)
            }
        )
    }

    private fun validateInputs(
        username: String, email: String, password: String, confirmPassword: String
    ): Boolean {
        if (username.length < 3) { showError("Username must be at least 3 characters."); return false }
        if (!username.matches(Regex("[a-zA-Z0-9_]+"))) { showError("Username: letters, numbers and _ only."); return false }
        if (email.isBlank()) { showError("Please enter your email address."); return false }
        if (password.length < 6) { showError("Password must be at least 6 characters."); return false }
        if (password != confirmPassword) { showError("Passwords do not match."); return false }
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
        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
