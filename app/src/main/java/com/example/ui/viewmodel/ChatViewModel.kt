package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatValidator
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagesInboxViewModel(
    private val repository: TrolleyRepository,
    private val currentUserId: String,
) : ViewModel() {

    val threadsState: StateFlow<UiState<List<ChatThread>>> = repository.observeChatThreads(currentUserId)
        .map { UiState.Success(it) as UiState<List<ChatThread>> }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat threads", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Unable to load messages."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private companion object {
        const val TAG = "MessagesInboxVM"
    }
}

data class ChatThreadSnapshot(
    val thread: ChatThread? = null,
    val messages: List<ChatMessage> = emptyList(),
    val providerProfile: ProviderProfile? = null,
    val isCurrentUserProvider: Boolean = false,
)

class ChatThreadViewModel(
    private val repository: TrolleyRepository,
    private val providerId: String,
    private val providerUserId: String,
    private val providerBusinessName: String,
    private val providerCategoryName: String,
    private val customerId: String,
    private val customerName: String,
    private val currentUserId: String,
    private val currentUserDisplayName: String,
) : ViewModel() {

    private val threadId = ChatValidator.deterministicThreadId(providerId, customerId)

    val uiState: StateFlow<UiState<ChatThreadSnapshot>> = combine(
        repository.observeChatThread(threadId, currentUserId),
        repository.observeChatMessages(threadId, currentUserId),
        repository.observeProviderProfileById(providerId),
    ) { thread, messages, provider ->
        UiState.Success(
            ChatThreadSnapshot(
                thread = thread,
                messages = messages,
                providerProfile = provider,
                isCurrentUserProvider = currentUserId == providerUserId,
            )
        ) as UiState<ChatThreadSnapshot>
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat thread $threadId", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Unable to open chat conversation."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _notice = MutableStateFlow<MutationNotice?>(null)
    val notice: StateFlow<MutationNotice?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun markThreadAsReadIfNeeded(thread: ChatThread?) {
        if (thread == null || !thread.hasUnreadFor(currentUserId)) return
        viewModelScope.launch {
            repository.markChatThreadRead(threadId)
        }
    }

    fun sendMessage(
        rawBody: String,
        messageType: String = "text",
        onSent: () -> Unit = {},
    ) {
        if (_isSending.value) return
        val clean = rawBody.trim()
        if (!ChatValidator.isValidMessageBody(clean)) {
            _notice.value = MutationNotice("Enter a message between 1 and 1,000 characters.", isError = true)
            return
        }

        val snapshot = (uiState.value as? UiState.Success)?.data
        val resolvedProvider = snapshot?.providerProfile
        val resolvedBusinessName = resolvedProvider?.businessName?.takeIf { it.isNotBlank() }
            ?: snapshot?.thread?.providerBusinessName?.takeIf { it.isNotBlank() }
            ?: providerBusinessName
        val resolvedCategoryName = resolvedProvider?.categoryName?.takeIf { it.isNotBlank() }
            ?: snapshot?.thread?.providerCategoryName?.takeIf { it.isNotBlank() }
            ?: providerCategoryName
        val resolvedCustomerName = snapshot?.thread?.customerName?.takeIf { it.isNotBlank() }
            ?: customerName.takeIf { it.isNotBlank() }
            ?: if (currentUserId == customerId) currentUserDisplayName else "Trolley Customer"
        val resolvedSenderName = currentUserDisplayName.ifBlank {
            if (currentUserId == providerUserId) resolvedBusinessName else resolvedCustomerName
        }

        viewModelScope.launch {
            _isSending.value = true
            _notice.value = null
            val result = repository.sendChatMessage(
                providerId = providerId,
                providerUserId = providerUserId,
                providerBusinessName = resolvedBusinessName,
                providerCategoryName = resolvedCategoryName,
                customerId = customerId,
                customerName = resolvedCustomerName,
                senderName = resolvedSenderName,
                body = clean,
                messageType = messageType,
            )
            _isSending.value = false
            result.fold(
                onSuccess = { onSent() },
                onFailure = { error ->
                    _notice.value = MutationNotice(
                        error.localizedMessage ?: "Failed to send message.",
                        isError = true,
                    )
                },
            )
        }
    }

    private companion object {
        const val TAG = "ChatThreadVM"
    }
}
