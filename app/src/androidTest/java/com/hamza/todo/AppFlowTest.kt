package com.hamza.todo

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hamza.todo.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end flows through the real app on a phone or tablet emulator. Each test uses
 * unique titles, so they don't depend on each other or on what is already stored.
 * Screenshots go to the app's external files dir; CI pulls them off the device.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule(order = 0)
    val notifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val id = (1000..9999).random()

    private val device: String
        get() = if (rule.activity.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"

    @Test
    fun quickAddUnderstandsDatePriorityAndTags() {
        val title = "Buy milk $id"
        openAddTask()
        rule.onNodeWithTag("quickAddInput").performTextInput("$title tomorrow 5pm !high #shop")
        waitFor(hasText("High priority"))
        rule.onAllNodesWithText("Tomorrow, 17:00").onFirst().assertExists()
        rule.onAllNodesWithText("#shop").onFirst().assertExists()
        shot("01-quick-add")
        rule.onNodeWithTag("quickAddInput").performImeAction()
        waitGone("quickAddInput")

        clickTab("Upcoming")
        waitFor(hasText(title))
        shot("02-upcoming")
    }

    @Test
    fun completeThenDeleteAndUndo() {
        val title = "Pay rent $id"
        addTask("$title today")
        waitFor(hasContentDescription("Mark as done: $title"))
        shot("03-today")

        rule.onNodeWithContentDescription("Mark as done: $title").performClick()
        waitFor(hasContentDescription("Mark as not done: $title"))
        shot("04-today-completed")
        rule.onNodeWithContentDescription("Mark as not done: $title").performClick()
        waitFor(hasContentDescription("Mark as done: $title"))

        // Delete from the task screen, then undo.
        rule.onAllNodesWithText(title).onFirst().performClick()
        waitFor(hasContentDescription("Delete task"))
        shot("05-task-detail")
        rule.onNodeWithContentDescription("Delete task").performClick()
        waitFor(hasText("Undo"))
        rule.onNodeWithText("Undo").performClick()
        waitFor(hasContentDescription("Mark as done: $title"))

        // Swipe the card to delete, then undo.
        rule.onAllNodesWithText(title).onFirst().performTouchInput { swipeLeft() }
        waitFor(hasText("Undo"))
        rule.onNodeWithText("Undo").performClick()
        waitFor(hasContentDescription("Mark as done: $title"))
    }

    @Test
    fun createListAndAddATaskToIt() {
        val list = "Errands $id"
        val task = "Post letter $id"
        clickTab("Lists")
        waitFor(hasText("New list", substring = true))
        shot("06-lists")
        rule.onNodeWithText("New list", substring = true).performClick()
        rule.onNodeWithTag("listNameInput").performTextInput(list)
        rule.onNodeWithText("Save").performClick()
        waitFor(hasText("Add a task to $list", substring = true))

        rule.onNodeWithText("Add a task to $list", substring = true).performClick()
        rule.onNodeWithTag("quickAddInput").performTextInput(task)
        rule.onNodeWithTag("quickAddInput").performImeAction()
        waitGone("quickAddInput")
        waitFor(hasText(task))
        shot("07-list-detail")
    }

    @Test
    fun searchSettingsAndDarkMode() {
        val title = "Call plumber $id"
        addTask("$title #home")
        rule.onNodeWithContentDescription("Search tasks").performClick()
        rule.onNodeWithTag("searchInput").performTextInput("plumber $id")
        waitFor(hasText(title))
        shot("08-search")
        rule.onNodeWithContentDescription("Back").performClick()

        clickTab("Lists")
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        waitFor(hasText("Sync across my devices"))
        shot("09-settings")

        rule.onNodeWithText("Dark").performClick()
        rule.waitForIdle()
        shot("10-settings-dark")
        // Phones show Settings full-screen (no tab bar); tablets keep the rail.
        if (device == "phone") rule.onNodeWithContentDescription("Back").performClick()
        clickTab("Today")
        rule.waitForIdle()
        shot("11-today-dark")
        clickTab("Lists")
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        waitFor(hasText("System"))
        rule.onNodeWithText("System").performClick()
    }

    // Helpers

    private fun openAddTask() {
        rule.onAllNodes(hasText("New task") or hasContentDescription("New task")).onFirst().performClick()
        waitFor(hasTestTag("quickAddInput"))
    }

    private fun addTask(text: String) {
        openAddTask()
        rule.onNodeWithTag("quickAddInput").performTextInput(text)
        rule.onNodeWithTag("quickAddInput").performImeAction()
        waitGone("quickAddInput")
    }

    /** Bottom-bar tab on phones, rail item on tablets (both are selectable). */
    private fun clickTab(label: String) {
        rule.onNode(hasText(label) and isSelectable()).performClick()
        rule.waitForIdle()
    }

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(tag: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$device-$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
