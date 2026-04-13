package com.group10.smartstudytimer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class Profile : Fragment() {

    private val authRepository by lazy { AuthRepository(requireContext()) }
    private val firebaseRepository by lazy { FirebaseRepository() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val repo = ProfileRepository(requireContext())
        val avatarView = view.findViewById<ImageView>(R.id.ivAvatar)
        val levelBadgeView = view.findViewById<ImageView>(R.id.ivLevelBadge)
        val levelBadgeText = view.findViewById<TextView>(R.id.tvLevelBadge)
        val bestRecordCard = view.findViewById<MaterialCardView>(R.id.cardBestRecord)
        val signOutButton = view.findViewById<Button>(R.id.signOutButton)
        val editProfileButton = view.findViewById<Button>(R.id.btnEditProfile)

        val openBadgeRulesDialog = {
            showBadgeRulesDialog()
        }
        levelBadgeView.setOnClickListener { openBadgeRulesDialog() }
        levelBadgeText.setOnClickListener { openBadgeRulesDialog() }

        var currentProfileHeader: ProfileHeader? = null

        fun renderProfileHeader(profile: ProfileHeader) {
            currentProfileHeader = profile

            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            AvatarAssets.bindAvatar(avatarView, profile.avatarId)

            view.findViewById<TextView>(R.id.tvUsername).text =
                profile.displayName.ifBlank { "No name set" }

            view.findViewById<TextView>(R.id.profileEmailText).text =
                "Email: ${user?.email ?: "No email available"}"
        }

        signOutButton.setOnClickListener {
            authRepository.signOut(requireActivity()) {
                startActivity(Intent(requireContext(), AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }
        }

        editProfileButton.setOnClickListener {
            val initialProfile = currentProfileHeader
            showEditProfileDialog(
                initialDisplayName = initialProfile?.displayName.orEmpty(),
                initialAvatarId = initialProfile?.avatarId ?: AvatarAssets.defaultAvatarId(requireContext()),
                onSave = { displayName, avatarId ->
                    firebaseRepository.updateCurrentUserProfileSettings(
                        displayName = displayName,
                        avatarId = avatarId,
                        onSuccess = {
                            Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                            repo.loadProfileHeader(
                                onSuccess = { updatedProfile -> renderProfileHeader(updatedProfile) },
                                onError = {
                                    Toast.makeText(requireContext(), "Profile updated, but refresh failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onError = {
                            Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        // Profile Info
        repo.loadProfileHeader(
            onSuccess = { profile ->
                renderProfileHeader(profile)
            },
            onError = {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

                view.findViewById<TextView>(R.id.tvUsername).text = "Unknown user"
                view.findViewById<TextView>(R.id.profileEmailText).text =
                    "Email: ${user?.email ?: "No email available"}"
            }
        )

        // Best score
        val bestRecord = repo.getTodayStudyRecord()

        if (bestRecord != null) {
            bestRecordCard.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvBestScore).text =
                bestRecord.focusScore.toString()

            view.findViewById<TextView>(R.id.tvBestDate).text =
                bestRecord.completedAt.toString()
        } else {
            bestRecordCard.visibility = View.GONE
        }

        // Records sharing
        val shareButton = view.findViewById<Button>(R.id.btnShare)

        shareButton.setOnClickListener {
            val repo = ProfileRepository(requireContext())

            repo.shareBestRecordImage(
                onSuccess = {
                    // optional: show toast
                },
                onNoData = {
                    Toast.makeText(requireContext(), "You don't have any study record for today yet. Please start a session first.", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(requireContext(), "Sharing failed", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Leaderboard
        val globalLeaderboardContainer = view.findViewById<LinearLayout>(R.id.containerGlobalLeaderboard)

        repo.loadLeaderboard(
            onSuccess = { allUsers ->

                val resultText = StringBuilder()

                allUsers.forEachIndexed { index, user ->

                    val label = user.displayName ?: "User ${index + 1}"

                    resultText.append(
                        "${index + 1}. $label — Score: ${user.score}\n"
                    )
                }

                renderGlobalLeaderboard(globalLeaderboardContainer, allUsers)
            },
            onError = {
                showLeaderboardError(globalLeaderboardContainer, "Failed to load leaderboard")
            }
        )

        // History list
        val container = view.findViewById<LinearLayout>(R.id.historyContainer)
        container.removeAllViews()

        val historyList = repo.getDailyScoreHistory()
        val streakLevel = StudyStreakLevels.resolve(historyList)
        levelBadgeView.setImageResource(streakLevel.badgeResId)
        levelBadgeText.text = streakLevel.label
        levelBadgeText.setTextColor(Color.parseColor("#5F6368"))
        levelBadgeView.contentDescription = "${streakLevel.label} level, ${streakLevel.streakDays} day streak"

        for (item in historyList) {

            val row = layoutInflater.inflate(
                R.layout.item_history,
                container,
                false
            )

            val tvDate = row.findViewById<TextView>(R.id.tvHistoryDate)
            val tvDetails = row.findViewById<TextView>(R.id.tvHistoryDetails)
            val tvScore = row.findViewById<TextView>(R.id.tvHistoryScoreText)
            val progress = row.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(
                R.id.progressHistoryScore
            )

            tvDate.text = item.date.toString()
            tvDetails.text = buildHistoryDetails(item)
            tvScore.text = item.score.toString()
            progress.progress = item.score.toInt()

            container.addView(row)
        }

        return view
    }

    private fun buildHistoryDetails(item: DailyScoreHistoryEntry): String {
        return "Study ${formatStudyMinutes(item.studyMinutes)}   " +
            "Breaks ${item.interruptionCount}   " +
            "Interrupted ${formatInterruptedSeconds(item.interruptedSeconds)}"
    }

    private fun renderGlobalLeaderboard(
        container: LinearLayout,
        entries: List<RankedLeaderboardEntry>
    ) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            showLeaderboardError(container, "No leaderboard data yet")
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        entries.forEach { entry ->
            val itemView = inflater.inflate(R.layout.item_leaderboard_entry, container, false)
            itemView.findViewById<TextView>(R.id.tvLeaderboardRank).text = entry.rank.toString()
            AvatarAssets.bindAvatar(
                itemView.findViewById(R.id.ivLeaderboardAvatar),
                entry.avatarId
            )
            val baseLabel = entry.username.ifBlank { entry.displayName }.ifBlank { "User ${entry.rank}" }
            val label = if (entry.userId == currentUserId) "$baseLabel (You)" else baseLabel
            itemView.findViewById<TextView>(R.id.tvLeaderboardName).text = label
            itemView.findViewById<TextView>(R.id.tvLeaderboardScore).text = "Score ${entry.score}"
            itemView.findViewById<TextView>(R.id.tvLeaderboardMinutes).text =
                formatStudyMinutes(entry.studyMinutes)
            bindLeaderboardLevel(
                userId = entry.userId,
                displayName = label,
                badgeView = itemView.findViewById(R.id.ivLeaderboardBadge),
                levelView = itemView.findViewById(R.id.tvLeaderboardLevel)
            )
            container.addView(itemView)
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

    private fun showLeaderboardError(container: LinearLayout, message: String) {
        container.removeAllViews()
        val textView = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }
        container.addView(textView)
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

    private fun formatInterruptedSeconds(seconds: Long): String {
        val totalMinutes = seconds / 60
        val remainingSeconds = seconds % 60
        return when {
            totalMinutes > 0 && remainingSeconds > 0 -> "${totalMinutes}m ${remainingSeconds}s"
            totalMinutes > 0 -> "${totalMinutes}m"
            else -> "${remainingSeconds}s"
        }
    }

    private fun showEditProfileDialog(
        initialDisplayName: String,
        initialAvatarId: String,
        onSave: (String, String) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val displayNameInput = dialogView.findViewById<TextInputEditText>(R.id.etDisplayName)
        val avatarOptionsContainer = dialogView.findViewById<LinearLayout>(R.id.containerAvatarOptions)
        val avatarIds = AvatarAssets.listAvatarIds(requireContext())

        displayNameInput.setText(initialDisplayName)
        displayNameInput.setSelection(displayNameInput.text?.length ?: 0)

        var selectedAvatarId = initialAvatarId.takeIf { it in avatarIds }
            ?: AvatarAssets.defaultAvatarId(requireContext())
        val avatarCards = linkedMapOf<String, MaterialCardView>()
        val surfaceColor = MaterialColors.getColor(dialogView, com.google.android.material.R.attr.colorSurface)
        val selectedContainerColor = MaterialColors.getColor(
            dialogView,
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        val outlineColor = MaterialColors.getColor(
            dialogView,
            com.google.android.material.R.attr.colorOutlineVariant
        )
        val primaryColor = MaterialColors.getColor(dialogView, androidx.appcompat.R.attr.colorPrimary)

        avatarIds.chunked(3).forEachIndexed { rowIndex, rowAvatarIds ->
            val rowView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (rowIndex == 0) 0 else dpToPx(12)
                }
            }

            rowAvatarIds.forEachIndexed { index, avatarId ->
                val card = layoutInflater.inflate(
                    R.layout.item_avatar_option,
                    rowView,
                    false
                ) as MaterialCardView
                card.layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(88),
                    1f
                ).apply {
                    when (index) {
                        0 -> marginEnd = dpToPx(6)
                        rowAvatarIds.lastIndex -> marginStart = dpToPx(6)
                        else -> {
                            marginStart = dpToPx(6)
                            marginEnd = dpToPx(6)
                        }
                    }
                }
                card.isCheckable = true
                card.strokeColor = outlineColor
                AvatarAssets.bindAvatar(card.findViewById(R.id.ivAvatarOption), avatarId)
                card.contentDescription = "$avatarId avatar"
                avatarCards[avatarId] = card
                rowView.addView(card)
            }

            repeat(3 - rowAvatarIds.size) {
                rowView.addView(
                    View(requireContext()),
                    LinearLayout.LayoutParams(0, 0, 1f).apply {
                        if (rowAvatarIds.isNotEmpty()) {
                            marginStart = dpToPx(6)
                        }
                    }
                )
            }
            avatarOptionsContainer.addView(rowView)
        }

        fun updateAvatarSelection() {
            avatarCards.forEach { (avatarId, card) ->
                val isSelected = avatarId == selectedAvatarId
                card.isChecked = isSelected
                card.strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                card.strokeColor = if (isSelected) primaryColor else outlineColor
                card.setCardBackgroundColor(if (isSelected) selectedContainerColor else surfaceColor)
            }
        }

        avatarCards.forEach { (avatarId, card) ->
            card.setOnClickListener {
                selectedAvatarId = avatarId
                updateAvatarSelection()
            }
        }
        updateAvatarSelection()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit profile")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayName = displayNameInput.text?.toString()?.trim().orEmpty()
                if (displayName.isBlank()) {
                    displayNameInput.error = "Username cannot be empty"
                    return@setOnClickListener
                }

                onSave(displayName, selectedAvatarId)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showBadgeRulesDialog() {
        val context = requireContext()
        val content = android.widget.ScrollView(context).apply {
            setPadding(dpToPx(12), 0, dpToPx(8), 0)
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            }

            val introView = TextView(context).apply {
                text = "Badge levels are based on your active study streak."
                textSize = 14f
                setTextColor(Color.parseColor("#202124"))
            }
            container.addView(introView)

            val levelsTitle = TextView(context).apply {
                text = "Level rules"
                textSize = 15f
                setTextColor(Color.parseColor("#202124"))
                setPadding(0, dpToPx(16), 0, dpToPx(8))
            }
            container.addView(levelsTitle)

            container.addView(createBadgeRuleRow(R.drawable.badge_explorer, "Explorer: 0 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_starter, "Starter: 1 day"))
            container.addView(createBadgeRuleRow(R.drawable.badge_rookie, "Rookie: 2-3 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_steady, "Steady: 4-6 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_pro, "Pro: 7-13 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_master, "Master: 14-29 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_elite, "Elite: 30-59 days"))
            container.addView(createBadgeRuleRow(R.drawable.badge_legend, "Legend: 60+ days"))

            val detailsView = TextView(context).apply {
                text = """
                    How streaks work:
                    Your streak counts consecutive study days up to your most recent study date.

                    Rank-down rule:
                    If you stop studying, your badge drops by 1 level for each missed day after your last study day.

                    Example:
                    An 8-day streak is Pro.
                    If you miss 2 days, it drops 2 levels to Rookie.
                """.trimIndent()
                textSize = 14f
                setTextColor(Color.parseColor("#202124"))
                setPadding(0, dpToPx(16), 0, 0)
            }
            container.addView(detailsView)
            addView(container)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Badge Rules")
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun createBadgeRuleRow(
        badgeResId: Int,
        text: String
    ): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22))
                setImageResource(badgeResId)
            })

            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(10)
                }
                this.text = text
                textSize = 14f
                setTextColor(Color.parseColor("#202124"))
            })
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
