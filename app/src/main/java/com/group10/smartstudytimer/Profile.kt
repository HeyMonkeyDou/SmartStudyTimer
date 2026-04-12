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
        val levelBadgeCard = view.findViewById<MaterialCardView>(R.id.cardLevelBadge)
        val levelBadgeText = view.findViewById<TextView>(R.id.tvLevelBadge)
        val signOutButton = view.findViewById<Button>(R.id.signOutButton)
        val editProfileButton = view.findViewById<Button>(R.id.btnEditProfile)

        var currentProfileHeader: ProfileHeader? = null

        fun renderProfileHeader(profile: ProfileHeader) {
            currentProfileHeader = profile

            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            avatarView.setImageResource(AvatarAssets.getAvatarResId(profile.avatarId))

            view.findViewById<TextView>(R.id.tvUsername).text =
                profile.displayName.ifBlank { "No name set" }

            view.findViewById<TextView>(R.id.profileEmailText).text =
                "Email: ${user?.email ?: "No email available"}"

            view.findViewById<TextView>(R.id.profileUidText).text =
                "UID: ${user?.uid ?: profile.userId}"
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
                initialAvatarId = initialProfile?.avatarId ?: AvatarAssets.CAT,
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

                view.findViewById<TextView>(R.id.profileUidText).text =
                    "UID: ${user?.uid ?: "No UID"}"
            }
        )

        // Best score
        val bestRecord = repo.getBestStudyRecord()

        if (bestRecord != null) {
            view.findViewById<TextView>(R.id.tvBestScore).text =
                bestRecord.focusScore.toString()

            view.findViewById<TextView>(R.id.tvBestDate).text =
                bestRecord.completedAt.toString()
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
                    Toast.makeText(requireContext(), "You don't have any records yet. Please start a session first.", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(requireContext(), "Sharing failed", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // History list
        val container = view.findViewById<LinearLayout>(R.id.historyContainer)
        container.removeAllViews()

        val historyList = repo.getDailyScoreHistory()
        val streakLevel = StudyStreakLevels.resolve(historyList)
        levelBadgeCard.setCardBackgroundColor(streakLevel.backgroundColor)
        levelBadgeText.text = streakLevel.label
        levelBadgeText.setTextColor(streakLevel.textColor)
        levelBadgeCard.contentDescription = "${streakLevel.label} level, ${streakLevel.streakDays} day streak"

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
        val avatarCards = linkedMapOf(
            AvatarAssets.CAT to dialogView.findViewById<MaterialCardView>(R.id.cardAvatarCat),
            AvatarAssets.CHICKEN to dialogView.findViewById<MaterialCardView>(R.id.cardAvatarChicken),
            AvatarAssets.PANDA to dialogView.findViewById<MaterialCardView>(R.id.cardAvatarPanda),
            AvatarAssets.RABBIT to dialogView.findViewById<MaterialCardView>(R.id.cardAvatarRabbit)
        )

        displayNameInput.setText(initialDisplayName)

        var selectedAvatarId = initialAvatarId.takeIf { it in AvatarAssets.avatarIds } ?: AvatarAssets.CAT

        fun updateAvatarSelection() {
            avatarCards.forEach { (avatarId, card) ->
                val isSelected = avatarId == selectedAvatarId
                card.strokeWidth = if (isSelected) dpToPx(3) else dpToPx(1)
                card.setCardBackgroundColor(
                    if (isSelected) Color.parseColor("#E8DEF8") else Color.WHITE
                )
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
            .setTitle("Edit Profile")
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

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
