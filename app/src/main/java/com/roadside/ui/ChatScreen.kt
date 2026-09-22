package com.roadside.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadside.agent.ChatMessage
import com.roadside.agent.MessageSender
import com.roadside.agent.VehicleContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    context: VehicleContext,
    onSendMessage: (String) -> Unit,
    onRecordSoundClicked: () -> Unit,
    onOpenCameraClicked: () -> Unit,
    onViewDiagnosisClicked: () -> Unit,
    onVoiceInputRequested: () -> Unit,
    onBackClicked: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(context.chatMessages.size) {
        if (context.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(context.chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RoadSide Conversation") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Action button bar at top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRecordSoundClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Record Sound")
                }

                Button(
                    onClick = onOpenCameraClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Camera")
                }
            }

            if (context.diagnosisCandidate != null || context.audioEvidence != null || context.visionEvidence != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onViewDiagnosisClicked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Diagnosis (${context.diagnosisCandidate?.title ?: "Preliminary"})")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chat message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(context.chatMessages) { message ->
                    ChatMessageItem(message = message)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type here...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    trailingIcon = {
                        IconButton(onClick = onVoiceInputRequested) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Speak")
                        }
                    }
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = if (isUser) "You" else "RoadSide",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
