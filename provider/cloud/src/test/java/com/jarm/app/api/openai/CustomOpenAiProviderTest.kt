package com.jarm.app.api.openai

import com.jarm.app.model.ModelThinkingCapabilities
import com.jarm.app.model.ModelThinkingCapability
import com.jarm.app.model.ThinkingProviderFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomOpenAiProviderTest {
    @Test
    fun undocumentedRelayModelsRetainPermissiveCapabilities() {
        listOf("llama-3.3-70b", "future-model", "glm-5.4").forEach { model ->
            assertEquals(ModelThinkingCapability.Permissive,
                ModelThinkingCapabilities.forModel(ThinkingProviderFamily.OPENAI_COMPATIBLE, model))
        }
    }

    @Test
    fun knownRelayModelsUseIntrinsicCapabilitiesAcrossNamespaces() {
        listOf("qwen3.8-27b", "Qwen/QWEN3.8-27B").forEach { model ->
            val capability = ModelThinkingCapabilities.forModel(
                ThinkingProviderFamily.OPENAI_COMPATIBLE, model,
            )
            assertEquals(listOf("low", "medium", "xhigh"), capability.supportedEfforts)
            assertTrue(capability.canDisableThinking)
            assertEquals("xhigh", capability.nearestEffort("max"))
        }
    }
}
