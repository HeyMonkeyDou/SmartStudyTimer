package com.group10.smartstudytimer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView

class Friends : Fragment() {

    private val firebaseRepository by lazy { FirebaseRepository() }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var containerIncomingRequests: LinearLayout
    private lateinit var tvNoRequests: TextView
    private lateinit var tvRequestBadge: TextView
    private lateinit var tvFriendsComparison: TextView

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
        val cardViewFriendList = view.findViewById<MaterialCardView>(R.id.cardViewFriendList)
        swipeRefresh = view.findViewById(R.id.swipeRefreshFriends)
        containerIncomingRequests = view.findViewById(R.id.containerIncomingRequests)
        tvNoRequests = view.findViewById(R.id.tvNoRequests)
        tvRequestBadge = view.findViewById(R.id.tvRequestBadge)
        tvFriendsComparison = view.findViewById(R.id.tvFriendsComparison)

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
                    refresh()
                },
                onError = {
                    if (!isAdded) return@sendFriendRequestByUsername
                    Toast.makeText(requireContext(), it.message ?: "Failed to send request.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ── My Friends entry ──────────────────────────────────────────────────

        cardViewFriendList.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FriendListFragment())
                .addToBackStack("friendList")
                .commit()
        }

        // ── Pull-to-refresh ───────────────────────────────────────────────────

        swipeRefresh.setOnRefreshListener { refresh() }

        // Initial load
        refresh()
    }

    private fun refresh() {
        loadIncomingRequests()
        loadFriendsComparison()
    }

    private fun loadIncomingRequests() {
        firebaseRepository.loadIncomingFriendRequests(
            onSuccess = { requests ->
                if (!isAdded) return@loadIncomingFriendRequests

                // Remove all existing request views (keep tvNoRequests)
                val inflater = LayoutInflater.from(requireContext())
                containerIncomingRequests.removeAllViews()

                if (requests.isEmpty()) {
                    tvNoRequests.visibility = View.VISIBLE
                    containerIncomingRequests.addView(tvNoRequests)
                    tvRequestBadge.visibility = View.GONE
                } else {
                    tvNoRequests.visibility = View.GONE
                    tvRequestBadge.text = requests.size.toString()
                    tvRequestBadge.visibility = View.VISIBLE

                    requests.forEach { request ->
                        val itemView = inflater.inflate(
                            R.layout.item_friend_request, containerIncomingRequests, false
                        )
                        itemView.findViewById<TextView>(R.id.tvRequestName).text =
                            request.fromDisplayName.ifBlank { request.fromEmail }
                        itemView.findViewById<TextView>(R.id.tvRequestSub).text =
                            "@${request.fromEmail}"

                        itemView.findViewById<ImageButton>(R.id.btnAccept).setOnClickListener {
                            firebaseRepository.acceptFriendRequest(
                                request = request,
                                onSuccess = {
                                    if (!isAdded) return@acceptFriendRequest
                                    Toast.makeText(requireContext(), "Friend request accepted.", Toast.LENGTH_SHORT).show()
                                    refresh()
                                },
                                onError = {
                                    if (!isAdded) return@acceptFriendRequest
                                    Toast.makeText(requireContext(), it.message ?: "Failed to accept.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        itemView.findViewById<ImageButton>(R.id.btnDecline).setOnClickListener {
                            firebaseRepository.declineFriendRequest(
                                requestId = request.requestId,
                                onSuccess = {
                                    if (!isAdded) return@declineFriendRequest
                                    Toast.makeText(requireContext(), "Friend request declined.", Toast.LENGTH_SHORT).show()
                                    loadIncomingRequests()
                                },
                                onError = {
                                    if (!isAdded) return@declineFriendRequest
                                    Toast.makeText(requireContext(), it.message ?: "Failed to decline.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        containerIncomingRequests.addView(itemView)
                    }
                }
                swipeRefresh.isRefreshing = false
            },
            onError = {
                if (!isAdded) return@loadIncomingFriendRequests
                containerIncomingRequests.removeAllViews()
                tvNoRequests.text = "Failed to load requests"
                tvNoRequests.visibility = View.VISIBLE
                containerIncomingRequests.addView(tvNoRequests)
                tvRequestBadge.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        )
    }

    private fun loadFriendsComparison() {
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
                        swipeRefresh.isRefreshing = false
                    },
                    onError = {
                        if (isAdded) {
                            tvFriendsComparison.text = "Failed to load friends"
                            swipeRefresh.isRefreshing = false
                        }
                    }
                )
            },
            onError = {
                if (isAdded) {
                    tvFriendsComparison.text = "Failed to load profile"
                    swipeRefresh.isRefreshing = false
                }
            }
        )
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }
}
