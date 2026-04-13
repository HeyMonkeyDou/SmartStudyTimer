package com.group10.smartstudytimer

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.LocalDate
import kotlin.text.get
import kotlin.text.set

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun ensureSignedInUser(
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser?.isAnonymous == true) {
            onError(IllegalStateException("Anonymous users are not allowed in this build."))
            return
        }
        val uid = currentUser?.uid
        if (!uid.isNullOrBlank()) {
            onSuccess(uid)
            return
        }

        onError(IllegalStateException("User is not signed in."))
    }

    fun getCurrentUserId(): String? {
        val uid = auth.currentUser?.uid
        return uid?.takeIf { it.isNotBlank() }
    }

    @Deprecated("Use ensureSignedInUser instead.")
    fun ensureAnonymousUser(
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(onSuccess, onError)
    }

    fun saveCurrentUserProfile(
        displayName: String,
        totalFocusMinutes: Long,
        avatarId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { uid ->
                saveUserProfile(
                    profile = UserProfile(
                        uid = uid,
                        displayName = displayName.ifBlank { uid.take(6) },
                        email = auth.currentUser?.email.orEmpty(),
                        totalFocusMinutes = totalFocusMinutes,
                        avatarId = AvatarAssets.resolveAvatarId(uid, avatarId)
                    ),
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun syncCurrentUserProfile(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { uid ->
                loadUserProfile(
                    uid = uid,
                    onSuccess = { existingProfile ->
                        val currentUser = auth.currentUser
                        saveUserProfile(
                            profile = UserProfile(
                                uid = uid,
                                displayName = existingProfile?.displayName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: currentUser?.displayName.orEmpty(),
                                email = currentUser?.email.orEmpty(),
                                username = existingProfile?.username.orEmpty(),
                                totalFocusMinutes = existingProfile?.totalFocusMinutes ?: 0L,
                                avatarId = existingProfile?.avatarId.orEmpty(),
                                bestFocusScore = existingProfile?.bestFocusScore ?: 0L,
                                bestFocusScoreCompletedAt = existingProfile?.bestFocusScoreCompletedAt.orEmpty(),
                                todayFocusScore = existingProfile?.todayFocusScore ?: 0L,
                                todayFocusScoreDate = existingProfile?.todayFocusScoreDate.orEmpty(),
                                todayStudyMinutes = existingProfile?.todayStudyMinutes ?: 0L
                            ),
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun updateCurrentUserProfileSettings(
        displayName: String,
        avatarId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { uid ->
                loadUserProfile(
                    uid = uid,
                    onSuccess = { existingProfile ->
                        val resolvedDisplayName = displayName.trim()
                            .ifBlank { existingProfile?.displayName.orEmpty().ifBlank { uid.take(6) } }
                        val resolvedAvatarId = AvatarAssets.resolveAvatarId(uid, avatarId)

                        saveUserProfile(
                            profile = UserProfile(
                                uid = uid,
                                displayName = resolvedDisplayName,
                                email = auth.currentUser?.email.orEmpty(),
                                username = existingProfile?.username.orEmpty(),
                                totalFocusMinutes = existingProfile?.totalFocusMinutes ?: 0L,
                                avatarId = resolvedAvatarId,
                                bestFocusScore = existingProfile?.bestFocusScore ?: 0L,
                                bestFocusScoreCompletedAt = existingProfile?.bestFocusScoreCompletedAt.orEmpty(),
                                todayFocusScore = existingProfile?.todayFocusScore ?: 0L,
                                todayFocusScoreDate = existingProfile?.todayFocusScoreDate.orEmpty(),
                                todayStudyMinutes = existingProfile?.todayStudyMinutes ?: 0L
                            ),
                            onSuccess = {
                                auth.currentUser?.updateProfile(
                                    UserProfileChangeRequest.Builder()
                                        .setDisplayName(resolvedDisplayName)
                                        .build()
                                )?.addOnCompleteListener {
                                    onSuccess()
                                } ?: onSuccess()
                            },
                            onError = onError
                        )
                    },
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
        val resolvedAvatarId = AvatarAssets.resolveAvatarId(profile.uid, profile.avatarId)
        val resolvedDisplayName = profile.displayName.ifBlank { profile.uid.take(6) }
        val payload = hashMapOf(
            "displayName" to resolvedDisplayName,
            "email" to profile.email,
            "username" to profile.username,
            "totalFocusMinutes" to profile.totalFocusMinutes,
            "avatarId" to resolvedAvatarId,
            "bestFocusScore" to profile.bestFocusScore,
            "bestFocusScoreCompletedAt" to profile.bestFocusScoreCompletedAt,
            "todayFocusScore" to profile.todayFocusScore,
            "todayFocusScoreDate" to profile.todayFocusScoreDate,
            "todayStudyMinutes" to profile.todayStudyMinutes,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(USERS_COLLECTION)
            .document(profile.uid)
            .set(payload, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error) }
    }

    fun loadCurrentUserProfile(
        onSuccess: (UserProfile?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
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

    fun loadLeaderboard(
        limit: Long = 10,
        onSuccess: (List<LeaderboardEntry>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val today = LocalDate.now().toString()
        firestore.collection(USERS_COLLECTION)
            .whereEqualTo("todayFocusScoreDate", today)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(
                    result.documents
                        .map { it.toLeaderboardEntry() }
                        .sortedWith(
                            compareByDescending<LeaderboardEntry> { it.todayFocusScore }
                                .thenByDescending { it.todayStudyMinutes }
                        )
                        .take(limit.toInt())
                )
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun updateCurrentBestFocusScore(
        bestFocusScore: Long,
        bestFocusScoreCompletedAt: String,
        todayFocusScore: Long,
        todayFocusScoreDate: String,
        todayStudyMinutes: Long,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        ensureSignedInUser(
            onSuccess = { uid ->
                loadUserProfile(
                    uid = uid,
                    onSuccess = { profile ->
                        val payload = hashMapOf(
                            "displayName" to profile?.displayName.orEmpty().ifBlank { uid.take(6) },
                            "avatarId" to AvatarAssets.resolveAvatarId(uid, profile?.avatarId),
                            "bestFocusScore" to bestFocusScore,
                            "bestFocusScoreCompletedAt" to bestFocusScoreCompletedAt,
                            "todayFocusScore" to todayFocusScore,
                            "todayFocusScoreDate" to todayFocusScoreDate,
                            "todayStudyMinutes" to todayStudyMinutes,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        firestore.collection(USERS_COLLECTION)
                            .document(uid)
                            .set(payload, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { error -> onError(error) }
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun saveCurrentStudySessions(
        sessions: List<StudySessionRecord>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
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
        ensureSignedInUser(
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
        ensureSignedInUser(
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
        ensureSignedInUser(
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

    fun sendFriendRequestByEmail(
        targetEmail: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val currentUser = auth.currentUser
                val currentDisplayName = currentUser?.displayName ?: currentUid.take(6)
                val currentEmail = currentUser?.email ?: ""

                firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("email", targetEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onError(Exception("User not found"))
                            return@addOnSuccessListener
                        }

                        val targetDoc = result.documents.first()
                        val targetUid = targetDoc.id

                        if (targetUid == currentUid) {
                            onError(Exception("You cannot add yourself"))
                            return@addOnSuccessListener
                        }

                        val requestRef = firestore.collection(FRIEND_REQUESTS_COLLECTION).document()

                        val payload = hashMapOf(
                            "requestId" to requestRef.id,
                            "fromUid" to currentUid,
                            "fromDisplayName" to currentDisplayName,
                            "fromEmail" to currentEmail,
                            "toUid" to targetUid,
                            "toEmail" to targetEmail,
                            "status" to "pending",
                            "createdAtEpochMillis" to System.currentTimeMillis()
                        )

                        requestRef.set(payload)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun loadIncomingFriendRequests(
        onSuccess: (List<FriendRequest>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                firestore.collection(FRIEND_REQUESTS_COLLECTION)
                    .whereEqualTo("toUid", currentUid)
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { result ->
                        val requests = result.documents.map { doc ->
                            FriendRequest(
                                requestId = doc.getString("requestId").orEmpty(),
                                fromUid = doc.getString("fromUid").orEmpty(),
                                fromDisplayName = doc.getString("fromDisplayName").orEmpty(),
                                fromEmail = doc.getString("fromEmail").orEmpty(),
                                toUid = doc.getString("toUid").orEmpty(),
                                toEmail = doc.getString("toEmail").orEmpty(),
                                status = doc.getString("status").orEmpty(),
                                createdAtEpochMillis = doc.getLong("createdAtEpochMillis") ?: 0L
                            )
                        }
                        onSuccess(requests)
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun acceptFriendRequest(
        request: FriendRequest,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val batch = firestore.batch()

        val requestRef = firestore.collection(FRIEND_REQUESTS_COLLECTION).document(request.requestId)

        val currentUserFriendRef = firestore.collection(USERS_COLLECTION)
            .document(request.toUid)
            .collection(FRIENDS_SUBCOLLECTION)
            .document(request.fromUid)

        val senderFriendRef = firestore.collection(USERS_COLLECTION)
            .document(request.fromUid)
            .collection(FRIENDS_SUBCOLLECTION)
            .document(request.toUid)

        val currentUserData = hashMapOf(
            "uid" to request.fromUid,
            "displayName" to request.fromDisplayName,
            "email" to request.fromEmail
        )

        val senderData = hashMapOf(
            "uid" to request.toUid,
            "displayName" to (auth.currentUser?.displayName ?: request.toUid.take(6)),
            "email" to (auth.currentUser?.email ?: "")
        )

        batch.update(requestRef, "status", "accepted")
        batch.set(currentUserFriendRef, currentUserData)
        batch.set(senderFriendRef, senderData)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun loadFriends(
        onSuccess: (List<FriendProfile>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                firestore.collection(USERS_COLLECTION)
                    .document(currentUid)
                    .collection(FRIENDS_SUBCOLLECTION)
                    .get()
                    .addOnSuccessListener { result ->
                        val basicFriends = result.documents.mapNotNull { doc ->
                            val uid = doc.getString("uid").orEmpty()
                            val nickname = doc.getString("nickname").orEmpty()
                            if (uid.isBlank()) null else Pair(uid, nickname)
                        }

                        if (basicFriends.isEmpty()) {
                            onSuccess(emptyList())
                            return@addOnSuccessListener
                        }

                        val nicknameMap = basicFriends.toMap()
                        val uidList = basicFriends.map { it.first }

                        firestore.collection(USERS_COLLECTION)
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), uidList)
                            .get()
                            .addOnSuccessListener { userDocs ->
                                val friends = userDocs.documents.map { doc ->
                                FriendProfile(
                                    uid = doc.id,
                                    displayName = doc.getString("displayName").orEmpty(),
                                    email = doc.getString("email").orEmpty(),
                                    username = doc.getString("username").orEmpty(),
                                    avatarId = AvatarAssets.resolveAvatarId(doc.id, doc.getString("avatarId")),
                                    bestFocusScore = doc.getLong("bestFocusScore") ?: 0L,
                                    totalFocusMinutes = doc.getLong("totalFocusMinutes") ?: 0L,
                                    todayFocusScore = doc.getLong("todayFocusScore") ?: 0L,
                                    todayFocusScoreDate = doc.getString("todayFocusScoreDate").orEmpty(),
                                    todayStudyMinutes = doc.getLong("todayStudyMinutes") ?: 0L,
                                    nickname = nicknameMap[doc.id].orEmpty()
                                )
                                }
                                onSuccess(friends)
                            }
                            .addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun updateUsername(
        username: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { uid ->
                firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(
                        mapOf(
                            "username" to username,
                            "displayName" to username,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun isUsernameAvailable(
        username: String,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .addOnSuccessListener { result -> onResult(result.isEmpty) }
            .addOnFailureListener { onError(it) }
    }

    fun sendFriendRequestByUsername(
        targetUsername: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val currentUser = auth.currentUser
                val currentDisplayName = currentUser?.displayName ?: currentUid.take(6)
                val currentEmail = currentUser?.email ?: ""

                firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("displayName", targetUsername)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onError(Exception("User \"$targetUsername\" not found"))
                            return@addOnSuccessListener
                        }
                        val targetDoc = result.documents.first()
                        val targetUid = targetDoc.id
                        if (targetUid == currentUid) {
                            onError(Exception("You cannot add yourself"))
                            return@addOnSuccessListener
                        }
                        val requestRef = firestore.collection(FRIEND_REQUESTS_COLLECTION).document()
                        requestRef.set(hashMapOf(
                            "requestId" to requestRef.id,
                            "fromUid" to currentUid,
                            "fromDisplayName" to currentDisplayName,
                            "fromEmail" to currentEmail,
                            "toUid" to targetUid,
                            "toEmail" to targetDoc.getString("email").orEmpty(),
                            "status" to "pending",
                            "createdAtEpochMillis" to System.currentTimeMillis()
                        ))
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun removeFriendByUsername(
        targetUsername: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("displayName", targetUsername)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onError(Exception("User \"$targetUsername\" not found"))
                            return@addOnSuccessListener
                        }
                        val targetUid = result.documents.first().id
                        if (targetUid == currentUid) {
                            onError(Exception("You cannot remove yourself"))
                            return@addOnSuccessListener
                        }
                        val batch = firestore.batch()
                        batch.delete(firestore.collection(USERS_COLLECTION).document(currentUid).collection(FRIENDS_SUBCOLLECTION).document(targetUid))
                        batch.delete(firestore.collection(USERS_COLLECTION).document(targetUid).collection(FRIENDS_SUBCOLLECTION).document(currentUid))
                        batch.commit()
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun removeFriendByEmail(
        targetEmail: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val normalizedEmail = targetEmail.trim().lowercase()

                firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("email", normalizedEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onError(Exception("Friend not found"))
                            return@addOnSuccessListener
                        }

                        val targetDoc = result.documents.first()
                        val targetUid = targetDoc.id

                        if (targetUid == currentUid) {
                            onError(Exception("You cannot remove yourself"))
                            return@addOnSuccessListener
                        }

                        val currentUserFriendRef = firestore.collection(USERS_COLLECTION)
                            .document(currentUid)
                            .collection(FRIENDS_SUBCOLLECTION)
                            .document(targetUid)

                        val targetUserFriendRef = firestore.collection(USERS_COLLECTION)
                            .document(targetUid)
                            .collection(FRIENDS_SUBCOLLECTION)
                            .document(currentUid)

                        val batch = firestore.batch()
                        batch.delete(currentUserFriendRef)
                        batch.delete(targetUserFriendRef)

                        batch.commit()
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }


    fun declineFriendRequest(
        requestId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(FRIEND_REQUESTS_COLLECTION)
            .document(requestId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun removeFriendByUid(
        friendUid: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val batch = firestore.batch()
                batch.delete(
                    firestore.collection(USERS_COLLECTION).document(currentUid)
                        .collection(FRIENDS_SUBCOLLECTION).document(friendUid)
                )
                batch.delete(
                    firestore.collection(USERS_COLLECTION).document(friendUid)
                        .collection(FRIENDS_SUBCOLLECTION).document(currentUid)
                )
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun updateFriendNickname(
        friendUid: String,
        nickname: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                firestore.collection(USERS_COLLECTION)
                    .document(currentUid)
                    .collection(FRIENDS_SUBCOLLECTION)
                    .document(friendUid)
                    .set(mapOf("nickname" to nickname), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun sendChatMessage(
        friendUid: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val chatId = listOf(currentUid, friendUid).sorted().joinToString("_")
                val msgRef = firestore.collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(MESSAGES_SUBCOLLECTION)
                    .document()
                val payload = hashMapOf(
                    "messageId" to msgRef.id,
                    "senderId" to currentUid,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
                msgRef.set(payload)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun listenToChatMessages(
        friendUid: String,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val currentUid = auth.currentUser?.uid ?: run {
            onError(Exception("Not signed in"))
            return firestore.collection("_dummy").addSnapshotListener { _, _ -> }
        }
        val chatId = listOf(currentUid, friendUid).sorted().joinToString("_")
        return firestore.collection(CHATS_COLLECTION)
            .document(chatId)
            .collection(MESSAGES_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                val messages = snapshot?.documents?.map { doc ->
                    ChatMessage(
                        messageId = doc.getString("messageId").orEmpty(),
                        senderId = doc.getString("senderId").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                onUpdate(messages)
            }
    }

    fun getLastChatMessage(
        friendUid: String,
        onSuccess: (ChatMessage?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val chatId = listOf(currentUid, friendUid).sorted().joinToString("_")
                firestore.collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(MESSAGES_SUBCOLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        val msg = result.documents.firstOrNull()?.let { doc ->
                            ChatMessage(
                                messageId = doc.getString("messageId").orEmpty(),
                                senderId = doc.getString("senderId").orEmpty(),
                                text = doc.getString("text").orEmpty(),
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }
                        onSuccess(msg)
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun getUnreadMessageCount(
        friendUid: String,
        sinceTimestamp: Long,
        onSuccess: (Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ensureSignedInUser(
            onSuccess = { currentUid ->
                val chatId = listOf(currentUid, friendUid).sorted().joinToString("_")
                firestore.collection(CHATS_COLLECTION)
                    .document(chatId)
                    .collection(MESSAGES_SUBCOLLECTION)
                    .whereGreaterThan("timestamp", sinceTimestamp)
                    .get()
                    .addOnSuccessListener { result ->
                        val count = result.documents.count { doc ->
                            doc.getString("senderId") != currentUid
                        }
                        onSuccess(count)
                    }
                    .addOnFailureListener { onError(it) }
            },
            onError = onError
        )
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val SESSIONS_COLLECTION = "study_sessions"
        private const val SESSION_ITEMS_SUBCOLLECTION = "items"
        private const val FRIEND_REQUESTS_COLLECTION = "friend_requests"
        private const val FRIENDS_SUBCOLLECTION = "friends"
        private const val CHATS_COLLECTION = "chats"
        private const val MESSAGES_SUBCOLLECTION = "messages"
    }

    private fun sessionDocuments(uid: String) =
        sessionRootDocument(uid).collection(SESSION_ITEMS_SUBCOLLECTION)

    private fun sessionRootDocument(uid: String) =
        firestore.collection(SESSIONS_COLLECTION).document(uid)
}

