package com.group10.smartstudytimer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class AuthActivity : AppCompatActivity() {

    private val authRepository by lazy { AuthRepository(this) }

    private lateinit var statusText: TextView
    private lateinit var signInButton: MaterialButton

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            setIdleState()
            Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        statusText.text = getString(R.string.signing_in)
        authRepository.authenticateWithGoogleResult(
            data = result.data,
            onSuccess = { openMainScreen() },
            onError = { error ->
                setIdleState()
                Toast.makeText(
                    this,
                    error.message ?: getString(R.string.google_sign_in_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        statusText = findViewById(R.id.authStatusText)
        signInButton = findViewById(R.id.googleSignInButton)

        signInButton.setOnClickListener { launchGoogleSignIn() }
    }

    override fun onStart() {
        super.onStart()
        if (authRepository.isSignedIn()) {
            openMainScreen()
        } else {
            setIdleState()
        }
    }

    private fun launchGoogleSignIn() {
        val signInIntent = authRepository.getGoogleSignInIntent()
        if (signInIntent == null) {
            Toast.makeText(
                this,
                getString(R.string.google_sign_in_not_configured),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        signInButton.isEnabled = false
        statusText.text = getString(R.string.signing_in)
        googleSignInLauncher.launch(signInIntent)
    }

    private fun openMainScreen() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setIdleState() {
        signInButton.isEnabled = true
        statusText.text = getString(R.string.auth_subtitle)
    }
}
