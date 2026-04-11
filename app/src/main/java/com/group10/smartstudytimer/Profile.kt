package com.group10.smartstudytimer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class Profile : Fragment() {

    private val authRepository by lazy { AuthRepository(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        val emailText = view.findViewById<TextView>(R.id.profileEmailText)
        val uidText = view.findViewById<TextView>(R.id.profileUidText)
        val signOutButton = view.findViewById<MaterialButton>(R.id.signOutButton)

        emailText.text = user?.email.orEmpty().ifBlank { "No email available" }
        uidText.text = user?.uid.orEmpty().ifBlank { "No user ID available" }

        signOutButton.setOnClickListener {
            signOutButton.isEnabled = false
            authRepository.signOut(requireActivity()) {
                Toast.makeText(requireContext(), getString(R.string.profile_signed_out), Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(requireContext(), AuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                requireActivity().finish()
            }
        }
    }
}
