package com.group10.smartstudytimer

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
    private lateinit var containerFriendsComparison: LinearLayout
    private lateinit var tvNewMessageBanner: TextView

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
        containerFriendsComparison = view.findViewById(R.id.containerFriendsComparison)
        tvNewMessageBanner = view.findViewById(R.id.tvNewMessageBanner)

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

        cardViewFriendList.setOnClickListener { openFriendList() }
        tvNewMessageBanner.setOnClickListener { openFriendList() }

        swipeRefresh.setOnRefreshListener { refresh() }
        refresh()
    }

    private fun openFriendList() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FriendListFragment())
            .addToBackStack("friendList")
            .commit()
    }

    private fun refresh() {
        loadIncomingRequests()
        loadFriendsComparison()
        checkUnreadMessages()
    }

    private fun checkUnreadMessages() {
        val currentUid = firebaseRepository.getCurrentUserUid() ?: return
        val prefs = requireContext().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

        firebaseRepository.loadFriends(
            onSuccess = { friends ->
                if (!isAdded) return@loadFriends
                if (friends.isEmpty()) {
                    updateUnreadUI(false)
                    return@loadFriends
                }

                var checked = 0
                var hasUnread = false

                friends.forEach { friend ->
                    val chatId = listOf(currentUid, friend.uid).sorted().joinToString("_")
                    val lastRead = prefs.getLong("last_read_$chatId", 0L)

                    firebaseRepository.getLastChatMessage(
                        friendUid = friend.uid,
                        onSuccess = { msg ->
                            if (msg != null && msg.senderId != currentUid && msg.timestamp > lastRead) {
                                hasUnread = true
                            }
                            checked++
                            if (checked == friends.size && isAdded) {
                                updateUnreadUI(hasUnread)
                            }
                        },
                        onError = {
                            checked++
                            if (checked == friends.size && isAdded) {
                                updateUnreadUI(hasUnread)
                            }
                        }
                    )
                }
            },
            onError = { /* silently ignore */ }
        )
    }

    private fun updateUnreadUI(hasUnread: Boolean) {
        tvNewMessageBanner.visibility = if (hasUnread) View.VISIBLE else View.GONE
        (activity as? MainActivity)?.setFriendsBadge(hasUnread)
    }

    private fun loadIncomingRequests() {
        firebaseRepository.loadIncomingFriendRequests(
            onSuccess = { requests ->
                if (!isAdded) return@loadIncomingFriendRequests

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
                            R.layout.item_friend_request,
                            containerIncomingRequests,
                            false
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
                            combined.add(
                                FriendProfile(
                                    uid = it.uid,
                                    displayName = it.displayName,
                                    username = if (it.username.isNotBlank()) "${it.username} (You)" else "(You)",
                                    email = it.email,
                                    avatarId = it.avatarId,
                                    bestFocusScore = it.bestFocusScore,
                                    totalFocusMinutes = it.totalFocusMinutes
                                )
                            )
                        }
                        combined.addAll(friends)
                        renderFriendsLeaderboard(combined)
                        swipeRefresh.isRefreshing = false
                    },
                    onError = {
                        if (!isAdded) return@loadFriends
                        showFriendsLeaderboardMessage("Failed to load friends")
                        swipeRefresh.isRefreshing = false
                    }
                )
            },
            onError = {
                if (!isAdded) return@loadCurrentUserProfile
                showFriendsLeaderboardMessage("Failed to load profile")
                swipeRefresh.isRefreshing = false
            }
        )
    }

    private fun renderFriendsLeaderboard(friends: List<FriendProfile>) {
        containerFriendsComparison.removeAllViews()
        if (friends.isEmpty()) {
            showFriendsLeaderboardMessage("No friends yet")
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        friends.sortedByDescending { it.bestFocusScore }
            .forEachIndexed { index, friend ->
                val itemView = inflater.inflate(
                    R.layout.item_leaderboard_entry,
                    containerFriendsComparison,
                    false
                )
                val displayName = friend.nickname.ifBlank { friend.username.ifBlank { friend.displayName } }

                itemView.findViewById<TextView>(R.id.tvLeaderboardRank).text = (index + 1).toString()
                AvatarAssets.bindAvatar(
                    itemView.findViewById(R.id.ivLeaderboardAvatar),
                    friend.avatarId
                )
                itemView.findViewById<TextView>(R.id.tvLeaderboardName).text = displayName
                itemView.findViewById<TextView>(R.id.tvLeaderboardScore).text = "Score ${friend.bestFocusScore}"
                itemView.findViewById<TextView>(R.id.tvLeaderboardMinutes).text =
                    formatStudyMinutes(friend.totalFocusMinutes)

                bindLeaderboardLevel(
                    userId = friend.uid,
                    displayName = displayName,
                    badgeView = itemView.findViewById(R.id.ivLeaderboardBadge),
                    levelView = itemView.findViewById(R.id.tvLeaderboardLevel)
                )

                containerFriendsComparison.addView(itemView)
            }
    }

    private fun bindLeaderboardLevel(
        userId: String,
        displayName: String,
        badgeView: ImageView,
        levelView: TextView
    ) {
        badgeView.setImageResource(R.drawable.badge_explorer)
        levelView.text = "Explorer"
        levelView.setTextColor(Color.parseColor("#5F6368"))

        firebaseRepository.loadStudySessions(
            uid = userId,
            onSuccess = { sessions ->
                if (!isAdded) return@loadStudySessions
                val history = StatisticsAggregator.buildDailyScoreRecords(sessions.orEmpty()).map { record ->
                    DailyScoreHistoryEntry(
                        date = record.date,
                        score = record.focusScore,
                        studyMinutes = record.studyMinutes,
                        interruptionCount = record.interruptionCount,
                        interruptedSeconds = record.interruptedSeconds
                    )
                }
                val streakLevel = StudyStreakLevels.resolve(history)
                badgeView.setImageResource(streakLevel.badgeResId)
                badgeView.contentDescription = "$displayName ${streakLevel.label} level"
                levelView.text = streakLevel.label
            },
            onError = {
                if (!isAdded) return@loadStudySessions
            }
        )
    }

    private fun showFriendsLeaderboardMessage(message: String) {
        containerFriendsComparison.removeAllViews()
        val textView = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        containerFriendsComparison.addView(textView)
    }

    private fun formatStudyMinutes(minutes: Long): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "${remainingMinutes}m"
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }
}
