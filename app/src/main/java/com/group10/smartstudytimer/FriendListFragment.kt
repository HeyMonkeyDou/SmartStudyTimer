package com.group10.smartstudytimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FriendListFragment : Fragment() {

    private val firebaseRepository by lazy { FirebaseRepository() }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var container: LinearLayout
    private lateinit var tvEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_friend_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarFriendList)
        swipeRefresh = view.findViewById(R.id.swipeRefreshFriendList)
        container = view.findViewById(R.id.containerFriendList)
        tvEmpty = view.findViewById(R.id.tvFriendListEmpty)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        swipeRefresh.setOnRefreshListener { loadFriends() }

        loadFriends()
    }

    private fun loadFriends() {
        firebaseRepository.loadFriends(
            onSuccess = { friends ->
                if (!isAdded) return@loadFriends
                renderFriends(friends)
                swipeRefresh.isRefreshing = false
            },
            onError = {
                if (!isAdded) return@loadFriends
                Toast.makeText(requireContext(), "Failed to load friends.", Toast.LENGTH_SHORT).show()
                swipeRefresh.isRefreshing = false
            }
        )
    }

    private fun renderFriends(friends: List<FriendProfile>) {
        container.removeAllViews()

        if (friends.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            container.addView(tvEmpty)
            return
        }

        tvEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())

        friends.forEach { friend ->
            val itemView = inflater.inflate(R.layout.item_friend, container, false)

            val ivAvatar = itemView.findViewById<ImageView>(R.id.ivFriendAvatar)
            val tvName = itemView.findViewById<TextView>(R.id.tvFriendName)
            val ivLevelBadge = itemView.findViewById<ImageView>(R.id.ivFriendLevelBadge)
            val tvLevel = itemView.findViewById<TextView>(R.id.tvFriendLevel)
            val tvUsername = itemView.findViewById<TextView>(R.id.tvFriendUsername)

            ivAvatar.setImageResource(AvatarAssets.getAvatarResId(friend.avatarId))

            val displayName = friend.nickname.ifBlank { friend.username.ifBlank { friend.displayName } }
            tvName.text = displayName
            tvUsername.text = "@${friend.username.ifBlank { friend.displayName }}"
            bindFriendLevel(friend, ivLevelBadge, tvLevel)

            // Single tap → open chat
            itemView.setOnClickListener {
                val chatFragment = ChatFragment.newInstance(
                    friendUid = friend.uid,
                    friendDisplayName = displayName
                )
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, chatFragment)
                    .addToBackStack("chat")
                    .commit()
            }

            // Long press → options menu
            itemView.setOnLongClickListener {
                showFriendOptions(friend, displayName)
                true
            }

            container.addView(itemView)
        }
    }

    private fun bindFriendLevel(
        friend: FriendProfile,
        ivLevelBadge: ImageView,
        tvLevel: TextView
    ) {
        ivLevelBadge.setImageResource(R.drawable.badge_explorer)
        tvLevel.text = "Explorer"
        tvLevel.setTextColor(0xFF5F6368.toInt())

        firebaseRepository.loadStudySessions(
            uid = friend.uid,
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

                ivLevelBadge.setImageResource(streakLevel.badgeResId)
                ivLevelBadge.contentDescription =
                    "${friend.displayName.ifBlank { friend.username }} ${streakLevel.label} level"
                tvLevel.text = streakLevel.label
                tvLevel.setTextColor(0xFF5F6368.toInt())
            },
            onError = {
                if (!isAdded) return@loadStudySessions
            }
        )
    }

    private fun showFriendOptions(friend: FriendProfile, currentDisplayName: String) {
        val options = arrayOf("Edit Notes", "Delete Friend")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(currentDisplayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditNicknameDialog(friend)
                    1 -> showRemoveFriendDialog(friend, currentDisplayName)
                }
            }
            .show()
    }

    private fun showEditNicknameDialog(friend: FriendProfile) {
        val input = EditText(requireContext()).apply {
            hint = "Enter a note title"
            setText(friend.nickname)
            setSelection(friend.nickname.length)
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Notes")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val nickname = input.text.toString().trim()
                firebaseRepository.updateFriendNickname(
                    friendUid = friend.uid,
                    nickname = nickname,
                    onSuccess = {
                        if (!isAdded) return@updateFriendNickname
                        Toast.makeText(requireContext(), "Note Updated.", Toast.LENGTH_SHORT).show()
                        loadFriends()
                    },
                    onError = {
                        if (!isAdded) return@updateFriendNickname
                        Toast.makeText(requireContext(), "Update Failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveFriendDialog(friend: FriendProfile, displayName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Friend")
            .setMessage("Are you sure to delete $displayName?")
            .setPositiveButton("Delete") { _, _ ->
                firebaseRepository.removeFriendByUid(
                    friendUid = friend.uid,
                    onSuccess = {
                        if (!isAdded) return@removeFriendByUid
                        Toast.makeText(requireContext(), "Deleted $displayName.", Toast.LENGTH_SHORT).show()
                        loadFriends()
                    },
                    onError = {
                        if (!isAdded) return@removeFriendByUid
                        Toast.makeText(requireContext(), "Fail to delete $displayName.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
