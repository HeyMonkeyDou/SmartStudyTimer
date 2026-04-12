package com.group10.smartstudytimer

import android.content.Context
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

class Friends : Fragment() {

    private val firebaseRepository by lazy { FirebaseRepository() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputFriendUsername = view.findViewById<EditText>(R.id.inputFriendUsername)
        val btnSendFriendRequest = view.findViewById<Button>(R.id.btnSendFriendRequest)
        val btnRefreshProfile = view.findViewById<Button>(R.id.btnRefreshProfile)
        val tvIncomingRequests = view.findViewById<TextView>(R.id.tvIncomingRequests)
        val tvFriendsComparison = view.findViewById<TextView>(R.id.tvFriendsComparison)
        val inputRemoveFriendUsername = view.findViewById<EditText>(R.id.inputRemoveFriendUsername)
        val btnRemoveFriend = view.findViewById<Button>(R.id.btnRemoveFriend)

        // ── Add friend ───────────────────────────────────────────────────────

        btnSendFriendRequest.setOnClickListener {
            val targetUsername = inputFriendUsername.text.toString().trim()
            if (targetUsername.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a username.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firebaseRepository.sendFriendRequestByUsername(
                targetUsername = targetUsername,
                onSuccess = {
                    if (!isAdded) return@sendFriendRequestByUsername
                    Toast.makeText(requireContext(), "Friend request sent.", Toast.LENGTH_SHORT).show()
                    inputFriendUsername.setText("")
                    hideKeyboard()
                    loadIncomingRequests(tvIncomingRequests, tvFriendsComparison)
                },
                onError = {
                    if (!isAdded) return@sendFriendRequestByUsername
                    Toast.makeText(requireContext(), it.message ?: "Failed to send request.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ── Remove friend ─────────────────────────────────────────────────────

        btnRemoveFriend.setOnClickListener {
            val targetUsername = inputRemoveFriendUsername.text.toString().trim()
            if (targetUsername.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a username.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firebaseRepository.removeFriendByUsername(
                targetUsername = targetUsername,
                onSuccess = {
                    if (!isAdded) return@removeFriendByUsername
                    Toast.makeText(requireContext(), "Friend removed.", Toast.LENGTH_SHORT).show()
                    inputRemoveFriendUsername.setText("")
                    hideKeyboard()
                    loadFriendsComparison(tvFriendsComparison)
                },
                onError = {
                    if (!isAdded) return@removeFriendByUsername
                    Toast.makeText(requireContext(), it.message ?: "Failed to remove friend.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ── Refresh ──────────────────────────────────────────────────────────

        btnRefreshProfile.setOnClickListener {
            loadIncomingRequests(tvIncomingRequests, tvFriendsComparison)
            Toast.makeText(requireContext(), "Refreshed", Toast.LENGTH_SHORT).show()
        }

        loadIncomingRequests(tvIncomingRequests, tvFriendsComparison)
    }

    private fun loadIncomingRequests(
        tvIncomingRequests: TextView,
        tvFriendsComparison: TextView
    ) {
        firebaseRepository.loadIncomingFriendRequests(
            onSuccess = { requests ->
                if (!isAdded) return@loadIncomingFriendRequests
                if (requests.isEmpty()) {
                    tvIncomingRequests.text = "No pending requests"
                    tvIncomingRequests.setOnClickListener(null)
                } else {
                    val text = buildString {
                        requests.forEach { request ->
                            append("From: ${request.fromDisplayName} (${request.fromEmail})\n")
                            append("Tap to accept\n\n")
                        }
                    }
                    tvIncomingRequests.text = text
                    val firstRequest = requests.first()
                    tvIncomingRequests.setOnClickListener {
                        firebaseRepository.acceptFriendRequest(
                            request = firstRequest,
                            onSuccess = {
                                if (!isAdded) return@acceptFriendRequest
                                Toast.makeText(requireContext(), "Friend request accepted.", Toast.LENGTH_SHORT).show()
                                loadIncomingRequests(tvIncomingRequests, tvFriendsComparison)
                                loadFriendsComparison(tvFriendsComparison)
                            },
                            onError = {
                                if (!isAdded) return@acceptFriendRequest
                                Toast.makeText(requireContext(), it.message ?: "Failed to accept.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            onError = {
                if (!isAdded) return@loadIncomingFriendRequests
                tvIncomingRequests.text = "Failed to load requests"
                tvIncomingRequests.setOnClickListener(null)
            }
        )
        loadFriendsComparison(tvFriendsComparison)
    }

    private fun loadFriendsComparison(tvFriendsComparison: TextView) {
        firebaseRepository.loadCurrentUserProfile(
            onSuccess = { currentUserProfile ->
                if (!isAdded) return@loadCurrentUserProfile
                firebaseRepository.loadFriends(
                    onSuccess = { friends ->
                        if (!isAdded) return@loadFriends
                        val combined = mutableListOf<FriendProfile>()
                        currentUserProfile?.let {
                            combined.add(FriendProfile(
                                uid = it.uid,
                                displayName = it.displayName,
                                username = if (it.username.isNotBlank()) "${it.username} (You)" else "(You)",
                                email = it.email,
                                avatarId = it.avatarId,
                                bestFocusScore = it.bestFocusScore,
                                totalFocusMinutes = it.totalFocusMinutes
                            ))
                        }
                        combined.addAll(friends)

                        if (combined.isEmpty()) {
                            tvFriendsComparison.text = "No friends yet"
                        } else {
                            val sorted = combined.sortedByDescending { it.bestFocusScore }
                            tvFriendsComparison.text = buildString {
                                append("Friends Leaderboard\n\n")
                                sorted.forEachIndexed { index, friend ->
                                    val label = friend.username.ifBlank { friend.displayName }
                                    append("${index + 1}. $label — Score: ${friend.bestFocusScore}, Minutes: ${friend.totalFocusMinutes}\n")
                                }
                            }
                        }
                    },
                    onError = { if (isAdded) tvFriendsComparison.text = "Failed to load friends" }
                )
            },
            onError = { if (isAdded) tvFriendsComparison.text = "Failed to load profile" }
        )
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }
}
