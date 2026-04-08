package com.group10.smartstudytimer

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun getCurrentUserId(): String? = auth.currentUser
        ?.takeUnless { it.isAnonymous }
        ?.uid

    fun isSignedIn(): Boolean = auth.currentUser?.isAnonymous == false

    fun buildGoogleSignInClient(): GoogleSignInClient? {
        val webClientId = resolveWebClientId()
        if (webClientId.isNullOrBlank()) {
            return null
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, options)
    }

    fun getGoogleSignInIntent(): Intent? = buildGoogleSignInClient()?.signInIntent

    fun authenticateWithGoogleResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = runCatching { task.getResult(Exception::class.java) }
            .getOrElse {
                onError(Exception(it.message ?: "Unable to get Google account.", it))
                return
            }

        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            onError(IllegalStateException("Google returned an empty ID token."))
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun signOut(activity: Activity? = null, onComplete: (() -> Unit)? = null) {
        auth.signOut()
        val client = buildGoogleSignInClient()
        if (client != null) {
            if (activity != null) {
                client.signOut().addOnCompleteListener(activity) { onComplete?.invoke() }
            } else {
                client.signOut().addOnCompleteListener { onComplete?.invoke() }
            }
        } else {
            onComplete?.invoke()
        }
    }

    private fun resolveWebClientId(): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resourceId == 0) {
            return null
        }

        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }
}
