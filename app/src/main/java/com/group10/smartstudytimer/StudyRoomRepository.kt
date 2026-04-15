package com.group10.smartstudytimer

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class StudyRoomRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        const val ROOMS_COLLECTION = "studyRooms"
        const val PARTICIPANTS_SUB = "participants"
        const val ROOM_INVITES_SUB = "roomInvites"
        const val SESSION_DURATION_MS = 45 * 60 * 1000L
        const val COUNTDOWN_DURATION_MS = 30 * 1000L
        const val MAX_DISTRACTIONS = 5
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid
    fun getCurrentDisplayName(): String {
        val user = auth.currentUser ?: return ""
        return user.displayName?.takeIf { it.isNotBlank() } ?: user.uid.take(6)
    }

    // ── Room lifecycle ───────────────────────────────────────────────────────

    fun createRoom(
        avatarId: String,
        onSuccess: (roomId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        val displayName = getCurrentDisplayName()

        val roomRef = firestore.collection(ROOMS_COLLECTION).document()
        val roomId = roomRef.id

        val roomData = hashMapOf(
            "roomId" to roomId,
            "hostUid" to uid,
            "status" to "WAITING",
            "countdownStartAt" to 0L,
            "sessionStartAt" to 0L,
            "endedAt" to 0L,
            "failedCount" to 0
        )
        val participantData = hashMapOf(
            "uid" to uid,
            "displayName" to displayName,
            "avatarId" to AvatarAssets.resolveAvatarId(uid, avatarId),
            "status" to "JOINED",
            "distractionCount" to 0,
            "joinedAt" to System.currentTimeMillis()
        )

        val batch = firestore.batch()
        batch.set(roomRef, roomData)
        batch.set(roomRef.collection(PARTICIPANTS_SUB).document(uid), participantData)
        batch.commit()
            .addOnSuccessListener { onSuccess(roomId) }
            .addOnFailureListener { onError(it) }
    }

    fun joinRoom(
        roomId: String,
        avatarId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        val displayName = getCurrentDisplayName()

        val roomRef = firestore.collection(ROOMS_COLLECTION).document(roomId)
        roomRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onError(Exception("Room not found")); return@addOnSuccessListener }
                val status = doc.getString("status") ?: ""
                if (status != "WAITING") { onError(Exception("Room is no longer accepting members")); return@addOnSuccessListener }

                val participantData = hashMapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "avatarId" to AvatarAssets.resolveAvatarId(uid, avatarId),
                    "status" to "JOINED",
                    "distractionCount" to 0,
                    "joinedAt" to System.currentTimeMillis()
                )
                roomRef.collection(PARTICIPANTS_SUB).document(uid).set(participantData)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun setReady(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
            .update("status", "READY")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun cancelReady(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
            .update("status", "JOINED")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Called by host when all participants are READY
    fun startCountdown(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .update(mapOf(
                "status" to "COUNTDOWN",
                "countdownStartAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Called by host when 30s countdown ends
    fun startSession(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val roomRef = firestore.collection(ROOMS_COLLECTION).document(roomId)
        roomRef.collection(PARTICIPANTS_SUB).get()
            .addOnSuccessListener { snap ->
                val batch = firestore.batch()
                batch.update(roomRef, mapOf(
                    "status" to "ACTIVE",
                    "sessionStartAt" to System.currentTimeMillis()
                ))
                snap.documents.forEach { p ->
                    batch.update(p.reference, "status", "STUDYING")
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun markCompleted(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
            .update("status", "COMPLETED")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun markFailed(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
            .update("status", "FAILED")
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Called by host when all participants are done (COMPLETED or FAILED)
    fun finalizeRoom(
        roomId: String,
        failedCount: Int,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .update(mapOf(
                "status" to "COMPLETED",
                "failedCount" to failedCount,
                "endedAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // Increment this user's distraction count; returns new count via onSuccess
    fun incrementDistraction(
        roomId: String,
        onSuccess: (newCount: Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        val ref = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val next = ((snap.getLong("distractionCount") ?: 0L) + 1).toInt()
            tx.update(ref, "distractionCount", next)
            next
        }
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { onError(it) }
    }

    fun leaveRoom(roomId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB).document(uid)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    fun listenToRoom(
        roomId: String,
        onUpdate: (StudyRoom?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(ROOMS_COLLECTION).document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                onUpdate(if (snap != null && snap.exists()) snap.toStudyRoom() else null)
            }
    }

    fun listenToParticipants(
        roomId: String,
        onUpdate: (List<RoomParticipant>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_SUB)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                onUpdate(snap?.documents?.map { it.toRoomParticipant() } ?: emptyList())
            }
    }

    // ── Invites ──────────────────────────────────────────────────────────────

    fun sendRoomInvite(
        roomId: String,
        targetDisplayName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        val displayName = getCurrentDisplayName()

        firestore.collection("users")
            .whereEqualTo("displayName", targetDisplayName)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) { onError(Exception("User \"$targetDisplayName\" not found")); return@addOnSuccessListener }
                val targetUid = result.documents.first().id
                if (targetUid == uid) { onError(Exception("Cannot invite yourself")); return@addOnSuccessListener }

                val inviteRef = firestore.collection("users").document(targetUid)
                    .collection(ROOM_INVITES_SUB).document(roomId)
                inviteRef.set(hashMapOf(
                    "inviteId" to roomId,
                    "roomId" to roomId,
                    "fromUid" to uid,
                    "fromDisplayName" to displayName,
                    "createdAt" to System.currentTimeMillis()
                ))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun loadPendingRoomInvites(
        onSuccess: (List<RoomInvite>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection("users").document(uid)
            .collection(ROOM_INVITES_SUB)
            .get()
            .addOnSuccessListener { snap ->
                onSuccess(snap.documents.map { it.toRoomInvite() })
            }
            .addOnFailureListener { onError(it) }
    }

    fun dismissRoomInvite(
        inviteId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = getCurrentUid() ?: run { onError(Exception("Not signed in")); return }
        firestore.collection("users").document(uid)
            .collection(ROOM_INVITES_SUB).document(inviteId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}
