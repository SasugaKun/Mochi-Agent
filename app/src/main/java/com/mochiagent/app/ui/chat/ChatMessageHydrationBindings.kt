package com.mochiagent.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mochiagent.app.data.CustomProviderConfig
import com.mochiagent.app.data.forDisplay
import com.mochiagent.app.model.ChatMessage
import com.mochiagent.app.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.Flow

internal data class ChatMessageHydrationBindings(
    val observeMessage: (String) -> Flow<ChatMessage?>,
    val searchMessages: suspend (String, List<String>) -> List<ChatMessage>,
)

@Composable
internal fun rememberChatMessageHydrationBindings(
    viewModel: ChatViewModel,
    customProviders: List<CustomProviderConfig>,
): ChatMessageHydrationBindings = remember(viewModel, customProviders) {
    ChatMessageHydrationBindings(
        observeMessage = { messageId ->
            viewModel.messagePayloadHydration.observeMessage(messageId) { message ->
                message.forDisplay(customProviders)
            }
        },
        searchMessages = { conversationId, messageIds ->
            viewModel.messagePayloadHydration.loadMessages(conversationId, messageIds) { message ->
                message.forDisplay(customProviders)
            }
        },
    )
}
