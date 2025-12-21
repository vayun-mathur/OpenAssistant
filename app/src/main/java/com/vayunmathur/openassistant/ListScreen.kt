package com.vayunmathur.openassistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vayunmathur.openassistant.data.dao.ConversationDao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(conversationDao: ConversationDao, selectedConversation: Long?, onConversationSelected: (Long) -> Unit) {
    val conversations by conversationDao.getAllConversations().collectAsState(listOf())
    Scaffold(
        topBar = { TopAppBar({Text("Conversations")}) }
    ) { paddingValues ->
        LazyColumn(Modifier.padding(paddingValues).fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(conversations) { conversation ->
                NavigationDrawerItem(
                    label = { Text(conversation.title) },
                    selected = selectedConversation == conversation.id,
                    onClick = {
                        onConversationSelected(conversation.id)
                    }
                )
            }
        }
    }
}