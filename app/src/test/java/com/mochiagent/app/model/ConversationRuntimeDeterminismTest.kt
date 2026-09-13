package com.mochiagent.app.model

import com.mochiagent.app.model.ConversationRuntimeReducerTestFixture.CONVERSATION_ID
import com.mochiagent.app.model.ConversationRuntimeReducerTestFixture.effectIdentity
import com.mochiagent.app.model.ConversationRuntimeReducerTestFixture.identity
import com.mochiagent.app.model.ConversationRuntimeReducerTestFixture.sendCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConversationRuntimeDeterminismTest {
    @Test
    fun `same command sequence produces equal states and effects`() {
        val send = sendCommand(ownerToken = 1, runId = "run", effectId = "input")
        val commands = listOf(
            send,
            ConversationCommand.InputPersisted(send.identity),
            ConversationCommand.StopRequested(
                identity(ownerToken = 1, runId = "run", pass = 0),
                coroutineAlreadySettled = false,
                requiresPersistence = true,
                effectId = "effect",
            ),
            ConversationCommand.PersistenceSettled(
                effectIdentity(identity(1, "run", 0), "effect"),
                success = true,
            ),
            ConversationCommand.CoroutineSettled(identity(1, "run", 0)),
        )

        fun replay(): List<Transition> {
            var state: RunState = RunState.Idle(CONVERSATION_ID)
            return commands.map { command ->
                ConversationRuntimeReducer.reduce(state, command).also { state = it.newState }
            }
        }

        assertEquals(replay(), replay())
        assertFalse(replay().any { it.rejection != null })
    }
}
