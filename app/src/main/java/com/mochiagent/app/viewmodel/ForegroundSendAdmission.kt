package com.mochiagent.app.viewmodel

import com.mochiagent.app.data.ConversationSettings
import com.mochiagent.app.data.local.ChatEntity
import com.mochiagent.app.data.local.NewChatPersistEntity
import com.mochiagent.app.model.SelectedAttachment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Immutable foreground destination captured synchronously when the Composer accepts a tap. */
internal data class ForegroundSendTarget(
    val ownerId: String,
    val conversationId: String,
    val runId: String,
    val wasNewChat: Boolean,
    val newChatEntryId: Long?,
    val modelId: String,
    val newChatWorkspace: NewChatWorkspaceSnapshot? = null,
) {
    init {
        require(ownerId.isNotBlank())
        require(conversationId.isNotBlank())
        require(runId.isNotBlank())
        require(wasNewChat == (ownerId == NEW_CHAT_WORKSPACE_ID))
        require(wasNewChat == (newChatEntryId != null))
        require(wasNewChat || newChatWorkspace == null)
        if (!wasNewChat) require(conversationId == ownerId)
    }
}

/** Full immutable generation admission captured before attachment waiting begins. */
internal data class ForegroundSendAdmission(
    val target: ForegroundSendTarget,
    val generationSnapshot: GenerationAdmissionSnapshot,
    val newConversation: ChatEntity?,
    val newConversationSettings: ConversationSettings?,
    val newChatPersistSnapshot: NewChatPersistEntity? = null,
) {
    init {
        require(generationSnapshot.conversationId == target.conversationId)
        require(generationSnapshot.runId == target.runId)
        require(newConversation == null || newConversation.id == target.conversationId)
        require(target.wasNewChat == (newConversation != null))
    }

    fun withSettledComposerDraft(
        acceptedText: String,
        settledAttachments: List<SelectedAttachment>,
    ): ForegroundSendAdmission {
        if (!target.wasNewChat || newChatPersistSnapshot == null) return this
        return copy(
            newChatPersistSnapshot = newChatPersistSnapshot.copy(
                draftText = acceptedText,
                draftAttachments = settledAttachments
                    .takeIf(List<*>::isNotEmpty)
                    ?.let(Json::encodeToString),
            ),
        )
    }
}
