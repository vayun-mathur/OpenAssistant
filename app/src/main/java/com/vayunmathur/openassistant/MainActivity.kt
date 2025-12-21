package com.vayunmathur.openassistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vayunmathur.openassistant.data.Conversation
import com.vayunmathur.openassistant.data.Message
import com.vayunmathur.openassistant.data.MessageDatabase
import com.vayunmathur.openassistant.data.Tools
import com.vayunmathur.openassistant.data.dao.ConversationDao
import com.vayunmathur.openassistant.data.dao.MessageDao
import com.vayunmathur.openassistant.data.toGrokMessage
import com.vayunmathur.openassistant.ui.theme.OpenAssistantTheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import kotlin.random.Random


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MessageDatabase.getDatabase(this)

        setContent {
            OpenAssistantTheme {
                Navigation(database)
            }
        }
    }
}

@Serializable
data object ListPage: NavKey

@Serializable
data class ChatPage(val conversationId: Long): NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun Navigation(database: MessageDatabase) {
    val backStack = rememberNavBackStack(ListPage)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    val conversationDao = database.conversationDao()
    val conversations by conversationDao.getAllConversations().collectAsState(listOf())
    val messageDao = database.messageDao()

    var selectedConversation: Long? by remember {mutableStateOf(null)}

    val coroutineScope = rememberCoroutineScope{Dispatchers.IO}

    LaunchedEffect(conversations) {
        if(conversations.isEmpty()) {
            if(backStack.firstOrNull() is ListPage) {
                backStack.removeAt(0)
            }
        } else {
            if(backStack.firstOrNull() !is ListPage) {
                backStack.add(0, ListPage)
            }
        }
    }

    Scaffold(contentWindowInsets = WindowInsets.ime) { paddingValues ->
        if(backStack.isEmpty()) {
            ChatScreen(conversationDao, messageDao, null, { }, {}, {
                backStack.add(ChatPage(it))
            }) { }
            return@Scaffold
        }
        NavDisplay(
            backStack,
            Modifier.padding(paddingValues),
            onBack = { backStack.removeAt(backStack.lastIndex) },
            sceneStrategy = listDetailStrategy,
            entryProvider = entryProvider {
                entry<ListPage>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    ChatScreen(conversationDao, messageDao, null, { }, {}, {
                        backStack.add(ChatPage(it))
                    }) { }
                })){
                    ListScreen(conversationDao, selectedConversation, {
                        if (backStack.last() is ChatPage) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                        backStack.add(ChatPage(it))
                    })
                }
                entry<ChatPage>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    DisposableEffect(Unit) {
                        selectedConversation = key.conversationId
                        onDispose {
                            selectedConversation = null
                        }
                    }
                    ChatScreen(conversationDao, messageDao, key.conversationId, {
                        backStack.removeAt(backStack.lastIndex)
                    }, {
                        coroutineScope.launch {
                            conversationDao.delete(key.conversationId)
                            backStack.removeAt(backStack.lastIndex)
                        }
                    }, {}, {backStack.removeAt(backStack.lastIndex)})
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationDao: ConversationDao,
    messageDao: MessageDao,
    conversationID: Long?,
    newConversation: () -> Unit,
    deleteConversation: () -> Unit,
    moveTo: (Long) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey ?: "") }
    var grokApi by remember { mutableStateOf(GrokApi(apiKey)) }
    val onApiKeyChanged = { newApiKey: String ->
        settingsManager.apiKey = newApiKey
        apiKey = newApiKey
        grokApi = GrokApi(newApiKey)
    }

    val coroutineScope = rememberCoroutineScope()

    val conversations by conversationDao.getAllConversations().collectAsState(listOf())
    val currentConversation = conversations.firstOrNull() { it.id == conversationID }
    val messages by messageDao.getMessagesForConversation(conversationID ?: -1).collectAsState(listOf())

    val visibleMessages by remember(messages, conversationID) {
        derivedStateOf {
            messages.filter { it.textContent.isNotBlank() && it.conversationId == conversationID }
        }
    }

    var userInput by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val selectedImageUris = remember { mutableStateOf<List<Uri>>(emptyList()) }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImageUris.value = uris
    }
    val lazyListState = rememberLazyListState()

    fun insertMessage(message: Message) {
        coroutineScope.launch {
            messageDao.insertMessage(message)
        }
    }

//    suspend fun createNewConversation() {
//        val newConversation = Conversation(title = "Conv #${conversations.size + 1}")
//        val id = conversationDao.insert(newConversation)
//        activeConversation = newConversation.copy(id = id)
//    }

//    fun deleteConversation(conversation: Conversation) {
//        coroutineScope.launch {
//            val wasActive = activeConversation?.id == conversation.id
//            messageDao.deleteMessagesForConversation(conversation.id)
//            conversationDao.delete(conversation)
//            if (wasActive) {
//                activeConversation = conversations.firstOrNull { it.id != conversation.id }
//            }
//        }
//    }

    LaunchedEffect(visibleMessages.size) {
        lazyListState.animateScrollToItem(if (visibleMessages.isEmpty()) 0 else visibleMessages.size - 1)
    }

    var newConversationID by remember {mutableStateOf(conversationID)}

    suspend fun requestResponse(userMessage: Message? = null) {
        isThinking = true

        val request = GrokRequest(
            messages = (messages + userMessage).filterNotNull().map(Message::toGrokMessage),
            model = "grok-4-fast-reasoning",
            stream = true,
            temperature = 0.7,
            tools = Tools.API_TOOLS
        )
        if (userMessage != null)
            insertMessage(userMessage)

        var assistantMessage = Message(
            id = Random.nextInt(),
            conversationId = newConversationID!!,
            role = "assistant",
            textContent = "",
            images = emptyList(),
            toolCalls = listOf()
        )
        insertMessage(assistantMessage)

        var fullResponse = ""
        var usedTools = false

        try {
            grokApi.getGrokCompletionStream(request) {
                coroutineScope.launch {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }.collect { chunk ->
                val delta = chunk.choices.first().delta
                delta.toolCalls?.forEach {
                    usedTools = true
                    val action = Tools.getToolAction(it.function.name)
                    if (action != null) {
                        val result = action(Json.decodeFromString(it.function.arguments), context)
                        val message = Message(
                            conversationId = newConversationID!!,
                            role = "tool",
                            textContent = result.llmResponse,
                            displayContent = result.userResponse,
                            images = emptyList(),
                            toolCallId = it.id,
                        )
                        insertMessage(message)
                    }
                    assistantMessage =
                        assistantMessage.copy(toolCalls = assistantMessage.toolCalls + it)
                    insertMessage(assistantMessage)
                }
                delta.content?.let {
                    fullResponse += it
                    assistantMessage = assistantMessage.copy(textContent = fullResponse)
                    insertMessage(assistantMessage) // This will be an upsert
                }
            }
        } catch (e: GrokApi.GrokException) {
            when (e.errorNum) {
                400, 401 -> {
                    // 400 means invalid api key, 401 means no api key included
                    if (e.errorNum == 400) {
                        Toast.makeText(context, "Invalid API key", Toast.LENGTH_SHORT).show()
                    }
                    showApiKeyDialog = true
                    e.printStackTrace()
                }

                else -> {
                    throw e
                }
            }
        } finally {
            isThinking = false
        }

        if (usedTools) {
            delay(1000)
            requestResponse()
        }
    }

    LaunchedEffect(messages) {
        if(messages.size == 1) {
            coroutineScope.launch {
                requestResponse()
                selectedImageUris.value = emptyList()
            }
        }
    }

    suspend fun send() {
        if(userInput.isBlank()) {
            return
        }
        if(newConversationID == null) {
            val newConversation = Conversation(title = "Conv #${conversations.size + 1}")
            val id = conversationDao.insert(newConversation)
            newConversationID = id
        }
        val imageBase64s = selectedImageUris.value.map { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT or Base64.NO_WRAP)
        }

        val userMessage = Message(
            conversationId = newConversationID!!,
            role = "user",
            textContent = userInput,
            images = imageBase64s
        )
        userInput = ""

        coroutineScope.launch {
            if(conversationID == null) {
                insertMessage(userMessage)
                moveTo(newConversationID!!)
            }
            else {
                requestResponse(userMessage)
                selectedImageUris.value = emptyList()
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showApiKeyDialog = false },
            onSave = { apiKey ->
                onApiKeyChanged(apiKey)
                showApiKeyDialog = false
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentConversation?.title ?: "New Conversation") },
                navigationIcon = {
                    IconButton(onClick = { coroutineScope.launch { back() } }) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        newConversation()
                    }) {
                        Icon(
                            painterResource(R.drawable.baseline_add_24),
                            contentDescription = "New Conversation"
                        )
                    }
                    IconButton(onClick = { deleteConversation() }) {
                        Icon(
                            painterResource(R.drawable.baseline_delete_24),
                            contentDescription = "Delete Conversation"
                        )
                    }
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(
                            painterResource(R.drawable.baseline_settings_24),
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Column {
                    OutlinedTextField(
                        userInput,
                        { userInput = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Ask Grok...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            coroutineScope.launch { send() }
                        }),
                        leadingIcon = {
                            IconButton(onClick = { imageLauncher.launch("image/*") }) {
                                Icon(
                                    painterResource(id = R.drawable.baseline_add_photo_alternate_24),
                                    contentDescription = "Add Image"
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton({
                                coroutineScope.launch { send() }
                            }) {
                                Icon(
                                    painterResource(R.drawable.outline_send_24),
                                    contentDescription = "Send"
                                )
                            }
                        }
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (visibleMessages.isEmpty() && !isThinking) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Send a message to start chatting!")
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { message ->
                        when (message.role) {
                            "user" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(start = 64.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            if (message.textContent.isNotBlank()) {
                                                Text(text = message.textContent)
                                            }
                                            if (message.images.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    items(message.images) { base64 ->
                                                        val imageBytes =
                                                            Base64.decode(
                                                                base64,
                                                                Base64.DEFAULT
                                                            )
                                                        val bitmap =
                                                            BitmapFactory.decodeByteArray(
                                                                imageBytes,
                                                                0,
                                                                imageBytes.size
                                                            )
                                                        Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(128.dp)
                                                                .clip(
                                                                    RoundedCornerShape(
                                                                        8.dp
                                                                    )
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "assistant" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Column(modifier = Modifier.padding(end = 64.dp)) {
                                        MarkdownText(
                                            markdown = message.displayContent ?: message.textContent,
                                            style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground)
                                        )
                                    }
                                }
                            }

                            else -> { // tool
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Column(modifier = Modifier.padding(end = 64.dp)) {
                                        Text(
                                            message.displayContent ?: message.textContent,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 64.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Thinking...")
                            }
                        }
                    }
                }
            }
            LazyRow(modifier = Modifier.padding(8.dp)) {
                items(selectedImageUris.value) { uri ->
                    val bitmap = remember {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                    val filename = uri.lastPathSegment
                    if (bitmap != null) {
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = {
                                Text(filename ?: "image")
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    selectedImageUris.value =
                                        selectedImageUris.value - uri
                                }) {
                                    Icon(
                                        painterResource(id = R.drawable.baseline_close_24),
                                        contentDescription = "Remove Image"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter API Key") },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiKey) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
