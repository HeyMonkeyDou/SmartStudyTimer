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

    fun saveCurrentStudySessions(
        sessions: List<StudySessionRecord>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> saveStudySessions(uid, sessions, onSuccess, onError) },
            onError = onError
        )
    }

    fun saveStudySessions(
        uid: String,
        sessions: List<StudySessionRecord>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        deleteStudySessions(
            uid = uid,
            onSuccess = {
                if (sessions.isEmpty()) {
                    onSuccess()
                    return@deleteStudySessions
                }

                val batch = firestore.batch()
                batch.set(
                    sessionRootDocument(uid),
                    hashMapOf(
                        "sessionCount" to sessions.size,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                sessions.forEach { session ->
                    val document = sessionDocuments(uid).document(session.sessionId)
                    batch.set(
                        document,
                        session.toFirestoreMap().apply { put("updatedAt", FieldValue.serverTimestamp()) }
                    )
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error -> onError(error) }
            },
            onError = onError
        )
    }

    fun addCurrentStudySession(
        session: StudySessionRecord,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> addStudySession(uid, session, onSuccess, onError) },
            onError = onError
        )
    }

    fun addStudySession(
        uid: String,
        session: StudySessionRecord,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val batch = firestore.batch()
        batch.set(
            sessionRootDocument(uid),
            hashMapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
        batch.set(
            sessionDocuments(uid).document(session.sessionId),
            session.toFirestoreMap().apply { put("updatedAt", FieldValue.serverTimestamp()) }
        )
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun loadCurrentStudySessions(
        onSuccess: (List<StudySessionRecord>?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> loadStudySessions(uid, onSuccess, onError) },
            onError = onError
        )
    }

    fun loadStudySessions(
        uid: String,
        onSuccess: (List<StudySessionRecord>?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        sessionDocuments(uid)
            .orderBy("endedAtEpochMillis", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    onSuccess(null)
                } else {
                    onSuccess(querySnapshot.documents.map { it.toStudySessionRecord() })
                }
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun deleteCurrentStudySessions(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureAnonymousUser(
            onSuccess = { uid -> deleteStudySessions(uid, onSuccess, onError) },
            onError = onError
        )
    }

    fun deleteStudySessions(
        uid: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        sessionDocuments(uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    sessionRootDocument(uid)
                        .delete()
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { error -> onError(error) }
                    return@addOnSuccessListener
                }

                val batch = firestore.batch()
                querySnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.delete(sessionRootDocument(uid))
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error -> onError(error) }
            }
            .addOnFailureListener { error -> onError(error) }
    }

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val LEADERBOARD_COLLECTION = "leaderboard"
        private const val SESSIONS_COLLECTION = "study_sessions"
        private const val SESSION_ITEMS_SUBCOLLECTION = "items"
    }

    private fun sessionDocuments(uid: String) =
        sessionRootDocument(uid).collection(SESSION_ITEMS_SUBCOLLECTION)

    private fun sessionRootDocument(uid: String) =
        firestore.collection(SESSIONS_COLLECTION).document(uid)
}
