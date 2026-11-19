package com.jarm.app.ui.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jarm.app.data.DefaultSystemPrompt
import com.jarm.app.data.HtmlPreviewGuidance
import com.jarm.app.data.PromptItemType
import com.jarm.app.data.PromptTemplateItem
import com.jarm.app.data.SystemPromptEntry
import com.jarm.app.data.migrateSystemPromptsOnStartup
import com.jarm.app.ui.motion.JARMMotionPolicy
import com.jarm.app.ui.motion.LocalJARMMotionPolicy
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w400dp-h900dp-mdpi")
class SystemPromptHtmlPreviewEditorTest {
    @get:Rule val compose = createComposeRule()
    private val saves = mutableListOf<SystemPromptEntry>()
    private var backs = 0
    private val removedAction = "Add HTML Preview Guidance"

    @Test
    fun defaultPromptKeepsOneTextItemPerVariableGap() {
        val items = DefaultSystemPrompt.create(Locale.ENGLISH).systemItems

        assertEquals(3, items.count { it.type == PromptItemType.CUSTOM })
        assertEquals(2, items.count { it.type == PromptItemType.PREDEFINED })
        items.zipWithNext().forEach { (left, right) ->
            assertFalse(
                "adjacent text items should be merged",
                left.type == PromptItemType.CUSTOM && right.type == PromptItemType.CUSTOM,
            )
        }
        assertTrue(HtmlPreviewGuidance.isPresent(items))
    }

    @Test
    fun insertMenuNoLongerOffersHtmlGuidance() {
        showEditor(entry(emptyList()))
        openInsert()
        compose.onNodeWithText("Add Text").assertExists()
        compose.onNodeWithText("Add Variable").assertExists()
        compose.onNodeWithText(removedAction).assertDoesNotExist()
    }

    @Test
    fun removingGuidanceTextFromTheMergedItemSurvivesRestart() {
        showEditor(DefaultSystemPrompt.create(Locale.ENGLISH))
        compose.onNode(hasSetTextAction() and hasText("## Interactive HTML preview", substring = true))
            .performTextReplacement("You are an assistant in JARM.")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle {
            var restored = saves.single()
            repeat(2) {
                restored = migrateSystemPromptsOnStartup(
                    Json.decodeFromString<List<SystemPromptEntry>>(Json.encodeToString(listOf(restored))),
                    Locale.ENGLISH,
                ).single()
                assertFalse(HtmlPreviewGuidance.isPresent(restored.systemItems))
            }
        }
    }

    @Test
    fun backDiscardDoesNotSaveEditedText() {
        showEditor(entry(emptyList()))
        compose.onNode(hasSetTextAction()).performTextReplacement("Draft text")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertTrue(saves.isEmpty())
        }
    }

    private fun openInsert(index: Int = 0) {
        compose.onAllNodesWithContentDescription("Insert Item")[index].performScrollTo().performClick()
    }

    private fun showEditor(entry: SystemPromptEntry) = compose.setContent {
        CompositionLocalProvider(LocalJARMMotionPolicy provides JARMMotionPolicy(reduceMotion = true)) {
            MaterialTheme {
                SystemPromptEditorPage(
                    entry = entry,
                    onSave = { title, system, user, assistant ->
                        saves += entry.copy(title = title, systemItems = system, userItems = user, assistantItems = assistant)
                    },
                    onBack = { backs += 1 },
                    showDocFab = false,
                )
            }
        }
    }

    private fun entry(items: List<PromptTemplateItem>) = SystemPromptEntry(
        title = "My prompt",
        systemItems = items,
        userItems = listOf(text("user prefix")),
        assistantItems = listOf(text("assistant suffix")),
    )

    private fun text(value: String) = PromptTemplateItem(type = PromptItemType.CUSTOM, value = value)
}
