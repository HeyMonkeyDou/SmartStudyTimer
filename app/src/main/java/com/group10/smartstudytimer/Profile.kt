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
import android.widget.Button
import android.widget.EditText

class Profile : Fragment() {

    private val authRepository by lazy { AuthRepository(requireContext()) }
    private val firebaseRepository by lazy { FirebaseRepository() }

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
        val inputFriendEmail = view.findViewById<EditText>(R.id.inputFriendEmail)
        val btnSendFriendRequest = view.findViewById<Button>(R.id.btnSendFriendRequest)
        val tvIncomingRequests = view.findViewById<TextView>(R.id.tvIncomingRequests)
        val tvFriendsComparison = view.findViewById<TextView>(R.id.tvFriendsComparison)

        emailText.text = user?.email.orEmpty().ifBlank { "No email available" }
        uidText.text = user?.uid.orEmpty().ifBlank { "No user ID available" }

        btnSendFriendRequest.setOnClickListener {
            val targetEmail = inputFriendEmail.text.toString().trim()

            if (targetEmail.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an email.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseRepository.sendFriendRequestByEmail(
                targetEmail = targetEmail,
                onSuccess = {
                    Toast.makeText(requireContext(), "Friend request sent.", Toast.LENGTH_SHORT).show()
                    inputFriendEmail.setText("")
                },
                onError = {
                    Toast.makeText(requireContext(), it.message ?: "Failed to send request.", Toast.LENGTH_SHORT).show()
                }
            )
        }
        firebaseRepository.loadIncomingFriendRequests(
            onSuccess = { requests ->
                if (requests.isEmpty()) {
                    tvIncomingRequests.text = "No pending requests"
                } else {
                    val text = buildString {
                        requests.forEach { request ->
                            append("From: ${request.fromDisplayName} (${request.fromEmail})\n")
                            append("Tap to accept this request\n\n")
                        }
                    }
                    tvIncomingRequests.text = text

                    val firstRequest = requests.first()
                    tvIncomingRequests.setOnClickListener {
                        firebaseRepository.acceptFriendRequest(
                            request = firstRequest,
                            onSuccess = {
                                Toast.makeText(requireContext(), "Friend request accepted.", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                Toast.makeText(requireContext(), it.message ?: "Failed to accept request.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            onError = {
                tvIncomingRequests.text = "Failed to load requests"
            }
        )
        firebaseRepository.loadFriends(
            onSuccess = { friends ->
                if (friends.isEmpty()) {
                    tvFriendsComparison.text = "No friends yet"
                } else {
                    val sortedFriends = friends.sortedByDescending { it.bestFocusScore }

                    val text = buildString {
                        append("Friends Leaderboard\n\n")
                        sortedFriends.forEachIndexed { index, friend ->
                            append("${index + 1}. ${friend.displayName} - Best Score: ${friend.bestFocusScore}, Total Minutes: ${friend.totalFocusMinutes}\n")
                        }
                    }

                    tvFriendsComparison.text = text
                }
            },
            onError = {
                tvFriendsComparison.text = "Failed to load friends"
            }
        )
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
