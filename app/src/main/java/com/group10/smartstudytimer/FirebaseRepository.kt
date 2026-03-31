package com.group10.smartstudytimer

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun ensureAnonymousUser(
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            onSuccess(currentUser.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    onError(IllegalStateException("Firebase returned an empty uid."))
                } else {
                    onSuccess(uid)
                }
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }

    fun saveCurrentUserProfile(
        displayName: String,
        totalFocusMinutes: Long,
        avatarId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid ->
                saveUserProfile(
                    profile = UserProfile(
                        uid = uid,
                        displayName = displayName,
                        totalFocusMinutes = totalFocusMinutes,
                        avatarId = avatarId
                    ),
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun saveUserProfile(
        profile: UserProfile,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val payload = hashMapOf(
            "displayName" to profile.displayName,
            "totalFocusMinutes" to profile.totalFocusMinutes,
            "avatarId" to profile.avatarId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(USERS_COLLECTION)
            .document(profile.uid)
            .set(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun loadCurrentUserProfile(
        onSuccess: (UserProfile?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> loadUserProfile(uid, onSuccess, onError) },
            onError = onError
        )
    }

    fun loadUserProfile(
        uid: String,
        onSuccess: (UserProfile?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onSuccess(snapshot.toUserProfile())
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun saveCurrentLeaderboardEntry(
        displayName: String,
        totalFocusMinutes: Long,
        avatarId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid ->
                saveLeaderboardEntry(
                    entry = LeaderboardEntry(
                        uid = uid,
                        displayName = displayName,
                        totalFocusMinutes = totalFocusMinutes,
                        avatarId = avatarId
                    ),
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun saveLeaderboardEntry(
        entry: LeaderboardEntry,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val payload = hashMapOf(
            "displayName" to entry.displayName,
            "totalFocusMinutes" to entry.totalFocusMinutes,
            "avatarId" to entry.avatarId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(LEADERBOARD_COLLECTION)
            .document(entry.uid)
            .set(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun loadLeaderboard(
        limit: Long = 10,
        onSuccess: (List<LeaderboardEntry>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(LEADERBOARD_COLLECTION)
            .orderBy("totalFocusMinutes", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.documents.map { it.toLeaderboardEntry() })
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun saveCurrentStudyStatistics(
        statistics: StudyStatistics,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid ->
                saveStudyStatistics(
                    statistics = statistics.copy(uid = uid),
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun saveStudyStatistics(
        statistics: StudyStatistics,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val payload = statistics.toFirestoreMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }

        firestore.collection(STATISTICS_COLLECTION)
            .document(statistics.uid)
            .set(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun loadCurrentStudyStatistics(
        onSuccess: (StudyStatistics?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> loadStudyStatistics(uid, onSuccess, onError) },
            onError = onError
        )
    }

    fun loadStudyStatistics(
        uid: String,
        onSuccess: (StudyStatistics?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(STATISTICS_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onSuccess(snapshot.toStudyStatistics())
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun deleteCurrentStudyStatistics(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> deleteStudyStatistics(uid, onSuccess, onError) },
            onError = onError
        )
    }

    fun deleteStudyStatistics(
        uid: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(STATISTICS_COLLECTION)
            .document(uid)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val LEADERBOARD_COLLECTION = "leaderboard"
        private const val STATISTICS_COLLECTION = "statistics"
    }
}
