package com.vayunmathur.openassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.vayunmathur.openassistant.data.MessageDatabase
import com.vayunmathur.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MessageDatabase.getDatabase(this)

        setContent {
            OpenAssistantTheme {
                Navigation(database, this::finish)
            }
        }
    }
}

@Serializable
data object ListPage: NavKey

@Serializable
data class ChatPage(val conversationId: Long): NavKey

@Serializable
data object NewChatPage: NavKey

@Serializable
data object APIKeyPopup: NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Navigation(database: MessageDatabase, finish: () -> Unit) {
    val backStack = rememberNavBackStack(ListPage, NewChatPage)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val dialogStrategy = DialogSceneStrategy<NavKey>()

    val context = LocalContext.current
    val settingsManager = SettingsManager(context)
    val apiKey by settingsManager.apiKey.collectAsState()
    val grokApi = GrokApi(apiKey)

    val conversationDao = database.conversationDao()
    val conversations by conversationDao.getAllConversations().collectAsState(listOf())
    val messageDao = database.messageDao()

    val coroutineScope = rememberCoroutineScope{Dispatchers.IO}

    LaunchedEffect(conversations) {
        if(conversations.isEmpty()) {
            // if no conversations, the only item in the backstack should be the newchatpage
            backStack.clear()
            backStack.add(NewChatPage)
        } else {
            // if there are conversations, the first item should be a listpage, and the next should be either a chatpage or a newchatpage or nothing
            if(backStack.firstOrNull() !is ListPage) {
                backStack.add(0, ListPage)
            }
        }
    }

    var isDetailPlaceholder by remember { mutableStateOf(false) }
    var isListShown by remember { mutableStateOf(false) }

    val selectedConversation = when(backStack.lastOrNull()) {
        is ChatPage -> (backStack.lastOrNull() as ChatPage).conversationId
        is NewChatPage -> 0
        else -> if(isDetailPlaceholder) 0 else null
    }

    Scaffold(contentWindowInsets = WindowInsets.displayCutout) { paddingValues ->
        if(backStack.isEmpty()) {
            return@Scaffold
        }
        NavDisplay(
            backStack,
            Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
            onBack = { if(backStack.size == 1 && backStack.first() is NewChatPage) finish() else backStack.removeAt(backStack.lastIndex) },
            sceneStrategy = dialogStrategy.then(listDetailStrategy),
            entryProvider = entryProvider {
                entry<ListPage>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    DisposableEffect(Unit) {
                        isDetailPlaceholder = true
                        onDispose {
                            isDetailPlaceholder = false
                        }
                    }
                    ChatScreen(conversationDao, messageDao, null, { backStack.add(ChatPage(it)) }, {backStack.add(APIKeyPopup)}, grokApi, {}, {}, null)
                })){
                    DisposableEffect(Unit) {
                        isListShown = true
                        onDispose {
                            isListShown = false
                        }
                    }
                    ListScreen(conversationDao, isDetailPlaceholder, selectedConversation, {
                        backStack.add(NewChatPage)
                    },{
                        if (backStack.last() is ChatPage || backStack.last() is NewChatPage) {
                            backStack[backStack.lastIndex] = ChatPage(it)
                        } else {
                            backStack.add(ChatPage(it))
                        }
                    })
                }
                entry<NewChatPage>(metadata = ListDetailSceneStrategy.detailPane()) {
                    ChatScreen(conversationDao, messageDao, null, {
                        backStack[backStack.lastIndex] = ChatPage(it)
                    }, {backStack.add(APIKeyPopup)}, grokApi,
                        {}, {},
                        if(isListShown) null else if(backStack.size == 1 && backStack.first() is NewChatPage) null else {
                            { backStack.removeAt(backStack.lastIndex) }
                        }
                    )
                }
                entry<ChatPage>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    ChatScreen(conversationDao, messageDao, key.conversationId, {
                        backStack[backStack.lastIndex] = NewChatPage
                    }, {backStack.add(APIKeyPopup)}, grokApi, {backStack[backStack.lastIndex] = NewChatPage}, {
                        coroutineScope.launch { conversationDao.delete(key.conversationId) }
                        backStack.removeAt(backStack.lastIndex)
                    }, if(isListShown) null else {{backStack.removeAt(backStack.lastIndex)}})
                }
                entry<APIKeyPopup>(metadata = DialogSceneStrategy.dialog()) {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            var apiKey by remember { mutableStateOf("") }
                            Text("Enter an xAI API key", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Button(onClick = { backStack.removeAt(backStack.lastIndex) }) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    settingsManager.setApiKey(apiKey)
                                    backStack.removeAt(backStack.lastIndex)
                                }) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
