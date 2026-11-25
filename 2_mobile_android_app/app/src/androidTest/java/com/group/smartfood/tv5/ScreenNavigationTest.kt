package com.group.smartfood.tv5

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.group.smartfood.R
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ScreenNavigationTest(
    private val number: Int,
    private val title: String,
    private val buttonId: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "SCR-{0}: {1}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf(16, "Checkout", R.id.tv5_open_16),
            arrayOf(17, "Vouchers", R.id.tv5_open_17),
            arrayOf(18, "Payment Method", R.id.tv5_open_18),
            arrayOf(19, "Order Confirmed", R.id.tv5_open_19),
            arrayOf(20, "Track Order", R.id.tv5_open_20),
            arrayOf(21, "Shipper", R.id.tv5_open_21),
            arrayOf(22, "Order Details", R.id.tv5_open_22),
            arrayOf(23, "Rate Your Order", R.id.tv5_open_23),
            arrayOf(24, "My Orders", R.id.tv5_open_24),
            arrayOf(25, "E-Receipt", R.id.tv5_open_25),
            arrayOf(26, "My Profile", R.id.tv5_open_26),
            arrayOf(27, "Loyalty Wallet", R.id.tv5_open_27),
            arrayOf(28, "Saved Addresses", R.id.tv5_open_28),
            arrayOf(29, "Notifications", R.id.tv5_open_29),
            arrayOf(30, "Help & Settings", R.id.tv5_open_30)
        )
    }

    @org.junit.Before fun prepareChatOrder() {
        if (number != 21) return
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val book = OrderBook(context.getSharedPreferences("tv5_demo", 0))
        val id = "navigation-" + java.util.UUID.randomUUID()
        book.put(book.quote(book.sampleCart(), "SMART20").put("id", id))
    }

    private fun checkTitle() {
        onView(allOf(withText(title), isDescendantOfA(withId(R.id.tv5_toolbar))))
            .check(matches(isDisplayed()))
    }

    private fun launchScreen(): ActivityScenario<Tv5ScreenActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), Tv5ScreenActivity::class.java)
            .putExtra(Tv5ScreenActivity.EXTRA_SCREEN, number)
        return ActivityScenario.launch(intent)
    }

    private fun revealGalleryButton(scenario: ActivityScenario<Tv5GalleryActivity>) {
        scenario.onActivity { activity ->
            val scroll = activity.findViewById<NestedScrollView>(R.id.tv5_root)
            scroll.scrollTo(0, activity.findViewById<View>(buttonId).top)
        }
    }

    @Test fun galleryOpensCorrectScreenAndToolbarBackReturns() {
        ActivityScenario.launch(Tv5GalleryActivity::class.java).use { gallery ->
            revealGalleryButton(gallery)
            onView(withId(buttonId)).perform(click())
            checkTitle()
            onView(withId(R.id.tv5_back)).perform(click())
            onView(withId(buttonId)).check(matches(isDisplayed()))
        }
    }

    @Test fun systemBackReturnsToGallery() {
        ActivityScenario.launch(Tv5GalleryActivity::class.java).use { gallery ->
            revealGalleryButton(gallery)
            onView(withId(buttonId)).perform(click())
            checkTitle()
            pressBack()
            onView(withId(buttonId)).check(matches(isDisplayed()))
        }
    }

    @Test fun recreationKeepsScreenAndContentCanReachBottom() {
        launchScreen().use { scenario ->
            checkTitle()
            scenario.recreate()
            checkTitle()
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<NestedScrollView>(R.id.tv5_scroll)
                assertTrue("Screen must have a measured scroll area", scroll.height > 0)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                assertFalse("Last content must be reachable", scroll.canScrollVertically(1))
                val header = activity.findViewById<android.view.ViewGroup>(R.id.tv5_toolbar)
                assertEquals(title, (header.getChildAt(1) as TextView).text.toString())
            }
        }
    }
}
