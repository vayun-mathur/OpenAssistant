package com.vayunmathur.openassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vayunmathur.openassistant.data.Conversation
import com.vayunmathur.openassistant.data.Message
import com.vayunmathur.openassistant.data.dao.ConversationDao
import com.vayunmathur.openassistant.data.dao.MessageDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationViewModel(private val conversationDao: ConversationDao, private val messageDao: MessageDao) : ViewModel() {

    val conversations = conversationDao.getAllConversations().stateIn(viewModelScope, started = SharingStarted.Eagerly, emptyList())

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            conversationDao.getAllConversations().collect { conversationList ->
                if (conversationList.isNotEmpty() && _activeConversation.value == null) {
                    setActiveConversation(conversationList.first())
                } else if (conversationList.isEmpty()) {
                    createNewConversation()
                }
            }
        }
    }

    fun setActiveConversation(conversation: Conversation) {
        _activeConversation.value = conversation
        _messages.value = emptyList()
        viewModelScope.launch {
            messageDao.getMessagesForConversation(conversation.id).collect {
                _messages.value = it
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newConversation = Conversation(title = "Conv #${conversations.value.size+1}")
            val id = conversationDao.insert(newConversation)
            setActiveConversation(newConversation.copy(id = id))
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.delete(conversation)
            if (_activeConversation.value?.id == conversation.id) {
                _activeConversation.value = null
                _messages.value = emptyList()

                // Optionally, set another conversation as active or create a new one
                conversations.value.firstOrNull{it.id != conversation.id}?.let { setActiveConversation(it) }
                    ?: createNewConversation()
            }
            messageDao.deleteMessagesForConversation(conversation.id)
        }
    }

    fun insertMessage(message: Message) {
        viewModelScope.launch {
            messageDao.insertMessage(message)
        }
    }
}

class ConversationViewModelFactory(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConversationViewModel(conversationDao, messageDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
