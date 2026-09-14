package com.mochiagent.app.api.ollama

import com.mochiagent.app.api.GenerationError
import com.mochiagent.app.api.ProviderConfig
import com.mochiagent.app.api.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaProviderConfigurationTest {
    @Test
    fun providerHasNoImplicitBaseUrl() {
        val provider = OllamaProvider()

        assertEquals("", provider.defaultBaseUrl)
        assertEquals("http://localhost:11434", provider.baseUrlPlaceholder)
    }

    @Test
    fun blankGenerationUrlFailsConfigurationWithoutFallback() = runBlocking {
        val events = OllamaProvider().generateResponse(
            messages = emptyList(),
            config = ProviderConfig(
                apiKey = "",
                modelId = "model",
                baseUrl = "  ",
            ),
        ).toList()

        val event = events.single() as StreamEvent.Error
        assertTrue(event.error is GenerationError.Configuration)
        assertEquals("Ollama base URL not configured", event.message)
    }

    @Test
    fun blankModelFetchUrlFailsBeforeNetworkDispatch() {
        listOf<String?>(null, "", "  ").forEach { baseUrl ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { OllamaProvider().fetchModels(apiKey = "", baseUrl = baseUrl) }
            }
            assertEquals("Ollama base URL not configured", error.message)
        }
    }
}
