package com.darshpandya.taskmaster

import android.Manifest
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.recyclerview.widget.RecyclerView
import com.darshpandya.taskmaster.data.Priority
import com.darshpandya.taskmaster.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFlowTest {
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var container: AppContainer
    private lateinit var reminders: RecordingReminderScheduler
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        installTestContainer().let { (c, r) -> container = c; reminders = r }
    }

    @After fun tearDown() { scenario?.close() }

    private fun seed(vararg titles: String) = runBlocking {
        titles.map { container.repository.add(it, "", null, Priority.MEDIUM) }
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    private fun item(title: String) = allOf(withId(R.id.title), withText(title))

    @Test fun emptyListShowsHint() {
        launch()
        onView(withText(R.string.empty_list)).check(matches(isDisplayed()))
    }

    @Test fun createTaskAppearsInList() {
        launch()
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_input)).perform(typeText("Buy groceries"))
        onView(withId(R.id.notes_input)).perform(typeText("milk, eggs"), closeSoftKeyboard())
        onView(withId(R.id.priority_high)).perform(click())
        onView(withId(R.id.save)).perform(click())

        onView(item("Buy groceries")).check(matches(isDisplayed()))
        onView(withText("milk, eggs")).check(matches(isDisplayed()))
        val saved = runBlocking { container.repository.getAll() }.single()
        assertEquals(Priority.HIGH, saved.priority)
    }

    @Test fun blankTitleShowsErrorAndDoesNotSave() {
        launch()
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.save)).perform(click())
        onView(withText("Title is required")).check(matches(isDisplayed()))
        assertTrue(runBlocking { container.repository.getAll() }.isEmpty())
    }

    @Test fun editTaskChangesTitle() {
        seed("Draft report")
        launch()
        onView(withId(R.id.list)).perform(actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText("Draft report")), click()))
        onView(withId(R.id.title_input)).check(matches(withText("Draft report")))
        onView(withId(R.id.title_input)).perform(clearText(), typeText("Final report"), closeSoftKeyboard())
        onView(withId(R.id.save)).perform(click())

        onView(item("Final report")).check(matches(isDisplayed()))
        onView(item("Draft report")).check(doesNotExist())
    }

    @Test fun completingTaskMovesItToDoneFilter() {
        seed("Pay rent", "Call mom")
        launch()
        onView(withId(R.id.list)).perform(actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText("Pay rent")), clickChild(R.id.done)))
        onView(withSubstring("1 open task")).check(matches(isDisplayed()))

        onView(withId(R.id.filter_active)).perform(click())
        onView(item("Pay rent")).check(doesNotExist())
        onView(item("Call mom")).check(matches(isDisplayed()))

        onView(withId(R.id.filter_done)).perform(click())
        onView(item("Pay rent")).check(matches(isDisplayed()))
        onView(item("Call mom")).check(doesNotExist())
    }

    @Test fun swipeDeletesAndUndoRestores() {
        seed("Swipe me")
        launch()
        onView(withId(R.id.list)).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, swipeLeft()))
        onView(item("Swipe me")).check(doesNotExist())
        onView(withText(R.string.undo)).perform(click())
        onView(item("Swipe me")).check(matches(isDisplayed()))
    }

    @Test fun deleteFromEditScreen() {
        seed("Remove me", "Keep me")
        launch()
        onView(withId(R.id.list)).perform(actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText("Remove me")), click()))
        onView(withId(R.id.action_delete)).perform(click())
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click())

        onView(item("Remove me")).check(doesNotExist())
        onView(item("Keep me")).check(matches(isDisplayed()))
    }

    @Test fun searchFiltersTheList() {
        seed("Buy groceries", "Book flights", "Buy stamps")
        launch()
        onView(withId(R.id.action_search)).perform(click())
        onView(isAssignableFrom(androidx.appcompat.widget.SearchView.SearchAutoComplete::class.java))
            .perform(typeText("buy"), closeSoftKeyboard())
        onView(item("Buy groceries")).check(matches(isDisplayed()))
        onView(item("Buy stamps")).check(matches(isDisplayed()))
        onView(item("Book flights")).check(doesNotExist())

        onView(isAssignableFrom(androidx.appcompat.widget.SearchView.SearchAutoComplete::class.java))
            .perform(replaceText("dentist"))
        onView(withText(R.string.empty_search)).check(matches(isDisplayed()))
    }

    @Test fun dueDatePickersScheduleAReminder() {
        val due = LocalDateTime.now().plusDays(2).withHour(9).withMinute(30).withSecond(0).withNano(0)
        launch()
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.title_input)).perform(typeText("Dentist"), closeSoftKeyboard())
        onView(withId(R.id.due_button)).perform(click())
        onView(isAssignableFrom(DatePicker::class.java))
            .perform(PickerActions.setDate(due.year, due.monthValue, due.dayOfMonth))
        onView(withId(android.R.id.button1)).perform(click())
        onView(isAssignableFrom(TimePicker::class.java)).perform(PickerActions.setTime(9, 30))
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(R.id.clear_due)).check(matches(isDisplayed()))
        onView(withId(R.id.save)).perform(click())

        val saved = runBlocking { container.repository.getAll() }.single()
        val expected = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, saved.dueAt)
        assertEquals(expected, reminders.pending[saved.id])
        onView(withSubstring("9:30")).check(matches(isDisplayed()))
    }

    @Test fun driveBackupIsShownAsNotConfigured() {
        launch()
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
        onView(withText("Google Drive backup (not configured)")).perform(click())
        onView(withText("Google Drive backup is not configured in this build (no OAuth client)."))
            .inRoot(isDialog()).check(matches(isDisplayed()))
    }
}
