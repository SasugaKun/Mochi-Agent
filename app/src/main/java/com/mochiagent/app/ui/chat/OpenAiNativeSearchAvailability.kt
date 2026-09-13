package com.mochiagent.app.ui.chat

import com.mochiagent.app.data.CustomProviderConfig
import com.mochiagent.app.data.isResponsesApiEnabledForProvider
import com.mochiagent.app.ui.common.AgoraHaptics
import com.mochiagent.app.viewmodel.ChatViewModel

internal fun resolveOpenAiNativeSearchAvailability(
    providerName: String,
    builtInOpenAiEnabled: Boolean,
    customProviders: List<CustomProviderConfig>,
): Boolean = isResponsesApiEnabledForProvider(
    providerName = providerName,
    builtInOpenAiEnabled = builtInOpenAiEnabled,
    customProviders = customProviders,
)

internal fun updateOpenAiNativeSearch(
    viewModel: ChatViewModel,
    conversationId: String?,
    haptics: AgoraHaptics,
    enabled: Boolean,
) {
    haptics.toggle(enabled)
    viewModel.updateConversationSetting(conversationId) {
        it.copy(openAiWebSearchEnabled = enabled)
    }
}
