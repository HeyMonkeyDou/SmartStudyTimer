package com.group10.smartstudytimer

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

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

        // Profile Info
        repo.loadProfileHeader(
            onSuccess = { profile ->

                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                avatarView.setImageResource(AvatarAssets.getAvatarResId(profile.avatarId))

                view.findViewById<TextView>(R.id.tvUsername).text =
                    profile.displayName ?: "No name set"

                view.findViewById<TextView>(R.id.profileEmailText).text =
                    "Email: ${user?.email ?: "No email available"}"

                view.findViewById<TextView>(R.id.profileUidText).text =
                    "UID: ${user?.uid ?: profile.userId}"
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
            val tvScore = row.findViewById<TextView>(R.id.tvHistoryScoreText)
            val progress = row.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(
                R.id.progressHistoryScore
            )

            tvDate.text = item.date.toString()
            tvScore.text = item.score.toString()
            progress.progress = item.score.toInt()

            container.addView(row)
        }

        return view
    }
}
