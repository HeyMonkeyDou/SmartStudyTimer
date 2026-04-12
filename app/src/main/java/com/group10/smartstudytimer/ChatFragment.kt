package com.group10.smartstudytimer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.ListenerRegistration

class ChatFragment : Fragment() {

    private val firebaseRepository by lazy { FirebaseRepository() }
    private var listenerRegistration: ListenerRegistration? = null

    private lateinit var containerMessages: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etChatInput: EditText

    private lateinit var friendUid: String
    private lateinit var friendDisplayName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        friendUid = arguments?.getString(ARG_FRIEND_UID) ?: ""
        friendDisplayName = arguments?.getString(ARG_FRIEND_NAME) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarChat)
        scrollView = view.findViewById(R.id.scrollViewMessages)
        containerMessages = view.findViewById(R.id.containerMessages)
        etChatInput = view.findViewById(R.id.etChatInput)
        val btnSend = view.findViewById<ImageButton>(R.id.btnSendMessage)

        toolbar.title = friendDisplayName
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnSend.setOnClickListener {
            val text = etChatInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            firebaseRepository.sendChatMessage(
                friendUid = friendUid,
                text = text,
                onSuccess = {
                    if (!isAdded) return@sendChatMessage
                    etChatInput.setText("")
                    hideKeyboard()
                },
                onError = {
                    if (!isAdded) return@sendChatMessage
                    Toast.makeText(requireContext(), "Failed to send message.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        startListening()
    }

    private fun startListening() {
        val currentUid = firebaseRepository.getCurrentUserUid() ?: return
        val chatId = listOf(currentUid, friendUid).sorted().joinToString("_")
        listenerRegistration = firebaseRepository.listenToChatMessages(
            friendUid = friendUid,
            onUpdate = { messages ->
                if (!isAdded) return@listenToChatMessages
                renderMessages(messages, currentUid)
                markAsRead(messages, currentUid, chatId)
            },
            onError = {
                if (!isAdded) return@listenToChatMessages
                Toast.makeText(requireContext(), "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun markAsRead(messages: List<ChatMessage>, currentUid: String, chatId: String) {
        val lastFriendMsg = messages.filter { it.senderId != currentUid }.maxByOrNull { it.timestamp }
        if (lastFriendMsg != null) {
            requireContext().getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putLong("last_read_$chatId", lastFriendMsg.timestamp)
                .apply()
        }
    }

    private fun renderMessages(messages: List<ChatMessage>, currentUid: String) {
        val inflater = LayoutInflater.from(requireContext())
        containerMessages.removeAllViews()

        messages.forEach { msg ->
            val itemView = inflater.inflate(R.layout.item_message, containerMessages, false)
            val containerSent = itemView.findViewById<LinearLayout>(R.id.containerSent)
            val containerReceived = itemView.findViewById<LinearLayout>(R.id.containerReceived)
            val tvSent = itemView.findViewById<TextView>(R.id.tvSentText)
            val tvReceived = itemView.findViewById<TextView>(R.id.tvReceivedText)

            if (msg.senderId == currentUid) {
                containerSent.visibility = View.VISIBLE
                containerReceived.visibility = View.GONE
                tvSent.text = msg.text
            } else {
                containerReceived.visibility = View.VISIBLE
                containerSent.visibility = View.GONE
                tvReceived.text = msg.text
            }

            containerMessages.addView(itemView)
        }

        // Scroll to bottom after layout
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    companion object {
        private const val ARG_FRIEND_UID = "friendUid"
        private const val ARG_FRIEND_NAME = "friendDisplayName"

        fun newInstance(friendUid: String, friendDisplayName: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FRIEND_UID, friendUid)
                    putString(ARG_FRIEND_NAME, friendDisplayName)
                }
            }
        }
    }
}
