package com.group10.smartstudytimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.group10.smartstudytimer.ui.theme.SmartStudyTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartStudyTimerTheme {
                FirebaseTestApp()
            }
        }
    }
}

private data class AvatarOption(
    val id: String,
    val label: String,
    val color: Color
)

data class LeaderboardEntryUi(
    val displayName: String = "",
    val totalFocusMinutes: Long = 0,
    val avatarId: String = ""
)

private val avatarOptions = listOf(
    AvatarOption("avatar_blue", "Blue", Color(0xFF5C6BC0)),
    AvatarOption("avatar_green", "Green", Color(0xFF66BB6A)),
    AvatarOption("avatar_orange", "Orange", Color(0xFFFFA726)),
    AvatarOption("avatar_red", "Red", Color(0xFFEF5350))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseTestApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    var displayName by rememberSaveable { mutableStateOf("Group10 Tester") }
    var totalFocusMinutesInput by rememberSaveable { mutableStateOf("120") }
    var avatarId by rememberSaveable { mutableStateOf(avatarOptions.first().id) }
    var currentUid by rememberSaveable { mutableStateOf(auth.currentUser?.uid.orEmpty()) }
    var statusMessage by rememberSaveable { mutableStateOf("Idle") }
    val logs = remember { mutableStateListOf<String>() }
    val leaderboardEntries = remember { mutableStateListOf<LeaderboardEntryUi>() }

    fun appendLog(message: String) {
        statusMessage = message
        logs.add(0, message)
        if (logs.size > 8) logs.removeLast()
    }

    fun ensureAnonymousUser(onReady: (String) -> Unit) {
        val existingUser = auth.currentUser
        if (existingUser != null) {
            currentUid = existingUser.uid
            appendLog("Using existing user: ${existingUser.uid}")
            onReady(existingUser.uid)
            return
        }

        appendLog("Signing in anonymously...")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                currentUid = uid
                appendLog("Anonymous sign-in success: $uid")
                onReady(uid)
            }
            .addOnFailureListener { error ->
                appendLog("Anonymous sign-in failed: ${error.message}")
            }
    }

    fun saveUserProfile(uid: String) {
        val totalMinutes = totalFocusMinutesInput.toLongOrNull()
        if (totalMinutes == null) {
            appendLog("Total focus minutes must be a number.")
            return
        }

        val payload = hashMapOf(
            "displayName" to displayName,
            "totalFocusMinutes" to totalMinutes,
            "avatarId" to avatarId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(uid)
            .set(payload)
            .addOnSuccessListener {
                appendLog("Saved user profile to Firestore.")
            }
            .addOnFailureListener { error ->
                appendLog("Save user profile failed: ${error.message}")
            }
    }

    fun loadUserProfile(uid: String) {
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    appendLog("No user profile found for $uid")
                    return@addOnSuccessListener
                }

                displayName = snapshot.getString("displayName").orEmpty()
                totalFocusMinutesInput =
                    snapshot.getLong("totalFocusMinutes")?.toString().orEmpty()
                avatarId = snapshot.getString("avatarId") ?: avatarOptions.first().id
                appendLog("Loaded user profile from Firestore.")
            }
            .addOnFailureListener { error ->
                appendLog("Load user profile failed: ${error.message}")
            }
    }

    fun upsertLeaderboard(uid: String) {
        val totalMinutes = totalFocusMinutesInput.toLongOrNull()
        if (totalMinutes == null) {
            appendLog("Total focus minutes must be a number.")
            return
        }

        val payload = hashMapOf(
            "displayName" to displayName,
            "totalFocusMinutes" to totalMinutes,
            "avatarId" to avatarId,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("leaderboard")
            .document(uid)
            .set(payload)
            .addOnSuccessListener {
                appendLog("Leaderboard entry saved.")
            }
            .addOnFailureListener { error ->
                appendLog("Save leaderboard failed: ${error.message}")
            }
    }

    fun loadLeaderboard() {
        firestore.collection("leaderboard")
            .orderBy("totalFocusMinutes", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                leaderboardEntries.clear()
                result.documents.forEach { document ->
                    leaderboardEntries.add(
                        LeaderboardEntryUi(
                            displayName = document.getString("displayName").orEmpty(),
                            totalFocusMinutes = document.getLong("totalFocusMinutes") ?: 0,
                            avatarId = document.getString("avatarId").orEmpty()
                        )
                    )
                }
                appendLog("Loaded ${leaderboardEntries.size} leaderboard entries.")
            }
            .addOnFailureListener { error ->
                appendLog("Load leaderboard failed: ${error.message}")
            }
    }

    val selectedAvatar = avatarOptions.firstOrNull { it.id == avatarId } ?: avatarOptions.first()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Firebase Test Console",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Status: $statusMessage",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "UID: ${currentUid.ifBlank { "Not signed in" }}",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") }
            )

            OutlinedTextField(
                value = totalFocusMinutesInput,
                onValueChange = { totalFocusMinutesInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Total focus minutes") }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Avatar Preview",
                        style = MaterialTheme.typography.titleMedium
                    )
                    AvatarPreview(option = selectedAvatar)
                    Text(
                        text = "Selected avatarId: $avatarId",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = "Choose Avatar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                avatarOptions.forEach { option ->
                    AvatarChip(
                        option = option,
                        isSelected = option.id == avatarId,
                        modifier = Modifier.weight(1f)
                    ) {
                        avatarId = option.id
                        appendLog("Selected avatar: ${option.id}")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        ensureAnonymousUser { }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sign In")
                }
                Button(
                    onClick = {
                        ensureAnonymousUser { uid ->
                            saveUserProfile(uid)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save User")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        ensureAnonymousUser { uid ->
                            loadUserProfile(uid)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Load User")
                }
                Button(
                    onClick = {
                        ensureAnonymousUser { uid ->
                            upsertLeaderboard(uid)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Rank")
                }
            }

            Button(
                onClick = { loadLeaderboard() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Leaderboard")
            }

            HorizontalDivider()

            Text(
                text = "Leaderboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (leaderboardEntries.isEmpty()) {
                Text("No leaderboard data loaded.")
            } else {
                leaderboardEntries.forEachIndexed { index, entry ->
                    LeaderboardRow(
                        rank = index + 1,
                        entry = entry
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Recent Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            logs.forEach { logEntry ->
                Text(text = logEntry, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AvatarPreview(option: AvatarOption) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(option.color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.label.take(1),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvatarChip(
    option: AvatarOption,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(option.color)
        )
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntryUi) {
    val avatar = avatarOptions.firstOrNull { it.id == entry.avatarId } ?: avatarOptions.first()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatar.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatar.label.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = entry.displayName.ifBlank { "Unnamed user" },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${entry.totalFocusMinutes} minutes",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = entry.avatarId.ifBlank { "no avatarId" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
