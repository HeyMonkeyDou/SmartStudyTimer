package com.group10.smartstudytimer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

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
        val usernameText = view.findViewById<TextView>(R.id.profileUsernameText)
        val btnChangeUsername = view.findViewById<Button>(R.id.btnChangeUsername)
        val usernameEditRow = view.findViewById<View>(R.id.usernameEditRow)
        val inputNewUsername = view.findViewById<EditText>(R.id.inputNewUsername)
        val btnSetUsername = view.findViewById<Button>(R.id.btnSetUsername)
        val usernameStatusText = view.findViewById<TextView>(R.id.usernameStatusText)
        val signOutButton = view.findViewById<MaterialButton>(R.id.signOutButton)
        val inputFriendUsername = view.findViewById<EditText>(R.id.inputFriendUsername)
        val btnSendFriendRequest = view.findViewById<Button>(R.id.btnSendFriendRequest)
        val tvIncomingRequests = view.findViewById<TextView>(R.id.tvIncomingRequests)
        val tvFriendsComparison = view.findViewById<TextView>(R.id.tvFriendsComparison)

        emailText.text = user?.email.orEmpty().ifBlank { "No email available" }
        uidText.text = user?.uid.orEmpty().ifBlank { "No user ID available" }

        fun showEditMode() {
            usernameEditRow.visibility = View.VISIBLE
            btnChangeUsername.visibility = View.GONE
            usernameStatusText.visibility = View.GONE
        }

        fun showDisplayMode(username: String) {
            usernameText.text = username.ifBlank { "(no username set)" }
            usernameEditRow.visibility = View.GONE
            btnChangeUsername.visibility = if (username.isNotBlank()) View.VISIBLE else View.GONE
            usernameStatusText.visibility = View.GONE
        }

        // Load username from Firestore
        firebaseRepository.loadCurrentUserProfile(
            onSuccess = { profile ->
                val current = profile?.username.orEmpty()
                if (current.isNotBlank()) {
                    inputNewUsername.setText(current)
                    showDisplayMode(current)
                } else {
                    showEditMode()
                }
            },
            onError = {
                usernameText.text = "(failed to load)"
                showEditMode()
            }
        )

        btnChangeUsername.setOnClickListener { showEditMode() }

        // Save username
        btnSetUsername.setOnClickListener {
            val newUsername = inputNewUsername.text.toString().trim()
            if (newUsername.isBlank() || newUsername.length < 3) {
                usernameStatusText.text = "Username must be at least 3 characters."
                usernameStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                usernameStatusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (!newUsername.matches(Regex("[a-zA-Z0-9_]+"))) {
                usernameStatusText.text = "Letters, numbers and _ only."
                usernameStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                usernameStatusText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnSetUsername.isEnabled = false
            usernameStatusText.text = "Checking…"
            usernameStatusText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            usernameStatusText.visibility = View.VISIBLE

            val currentDisplayed = usernameText.text.toString()
            firebaseRepository.isUsernameAvailable(
                username = newUsername,
                onResult = { available ->
                    if (!available && newUsername != currentDisplayed) {
                        usernameStatusText.text = "Username already taken."
                        usernameStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                        btnSetUsername.isEnabled = true
                        return@isUsernameAvailable
                    }
                    firebaseRepository.updateUsername(
                        username = newUsername,
                        onSuccess = {
                            btnSetUsername.isEnabled = true
                            hideKeyboard()
                            showDisplayMode(newUsername)
                        },
                        onError = {
                            usernameStatusText.text = "Save failed: ${it.message}"
                            usernameStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                            usernameStatusText.visibility = View.VISIBLE
                            btnSetUsername.isEnabled = true
                        }
                    )
                },
                onError = {
                    usernameStatusText.text = "Error: ${it.message}"
                    usernameStatusText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    usernameStatusText.visibility = View.VISIBLE
                    btnSetUsername.isEnabled = true
                }
            )
        }

        btnSendFriendRequest.setOnClickListener {
            val targetUsername = inputFriendUsername.text.toString().trim()
            if (targetUsername.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a username.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firebaseRepository.sendFriendRequestByUsername(
                targetUsername = targetUsername,
                onSuccess = {
                    Toast.makeText(requireContext(), "Friend request sent.", Toast.LENGTH_SHORT).show()
                    inputFriendUsername.setText("")
                    loadIncomingRequests(tvIncomingRequests)
                    loadFriendsComparison(tvFriendsComparison)
                },
                onError = {
                    Toast.makeText(requireContext(), it.message ?: "Failed to send request.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        loadIncomingRequests(tvIncomingRequests)
        loadFriendsComparison(tvFriendsComparison)

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

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }

    private fun loadIncomingRequests(tvIncomingRequests: TextView) {
        firebaseRepository.loadIncomingFriendRequests(
            onSuccess = { requests ->
                if (requests.isEmpty()) {
                    tvIncomingRequests.text = "No pending requests"
                    tvIncomingRequests.setOnClickListener(null)
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
                                loadIncomingRequests(tvIncomingRequests)
                                view?.findViewById<TextView>(R.id.tvFriendsComparison)?.let { loadFriendsComparison(it) }
                            },
                            onError = {
                                Toast.makeText(requireContext(), it.message ?: "Failed to accept request.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            onError = { tvIncomingRequests.text = "Failed to load requests" }
        )
    }

    private fun loadFriendsComparison(tvFriendsComparison: TextView) {
        firebaseRepository.loadFriends(
            onSuccess = { friends ->
                if (friends.isEmpty()) {
                    tvFriendsComparison.text = "No friends yet"
                } else {
                    val sortedFriends = friends.sortedByDescending { it.bestFocusScore }
                    val text = buildString {
                        append("Friends Leaderboard\n\n")
                        sortedFriends.forEachIndexed { index, friend ->
                            val nameLabel = friend.username.ifBlank { friend.displayName }
                            append("${index + 1}. $nameLabel — Best Score: ${friend.bestFocusScore}, Total Minutes: ${friend.totalFocusMinutes}\n")
                        }
                    }
                    tvFriendsComparison.text = text
                }
            },
            onError = { tvFriendsComparison.text = "Failed to load friends" }
        )
    }
}
