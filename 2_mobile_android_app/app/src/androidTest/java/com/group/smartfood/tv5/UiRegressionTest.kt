package com.group.smartfood.tv5

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group.smartfood.R
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    private fun launch(number: Int?): ActivityScenario<Tv5ScreenActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), Tv5ScreenActivity::class.java)
        if (number in listOf(21, 23, 25)) intent.putExtra("order_id", "test-order")
        if (number != null) intent.putExtra(Tv5ScreenActivity.EXTRA_SCREEN, number)
        return ActivityScenario.launch(intent)
    }

    private fun assertCheckout() {
        onView(allOf(withText("Checkout"), isDescendantOfA(withId(R.id.tv5_toolbar))))
            .check(matches(isDisplayed()))
    }

    @Test fun missingScreenExtraDefaultsToCheckout() {
        launch(null).use { assertCheckout() }
    }

    @Test fun outOfRangeScreenNumbersDefaultToCheckout() {
        for (number in listOf(Int.MIN_VALUE, -1, 0, 15, 31, Int.MAX_VALUE)) {
            launch(number).use { assertCheckout() }
        }
    }

    private fun seedCompletedOrder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("tv5_demo", 0)
        val book = OrderBook(prefs)
        val order = book.quote(book.sampleCart(), "FOODFLOW20")
            .put("id", "test-order").put("status", "COMPLETED").put("method", "COD")
            .put("payment", "COD").put("token", "unit-test-token")
        book.put(order)
    }

    @Test fun editableReviewUpdatesCounter() {
        seedCompletedOrder()
        launch(23).use { scenario ->
            scenario.onActivity { activity ->
                val review = activity.findViewById<EditText>(R.id.tv5_review_text)
                val counter = activity.findViewById<TextView>(R.id.tv5_review_counter)
                review.setText("Good")
                assertEquals("4 / 500", counter.text.toString())
            }
        }
    }

    @Test fun chatAcceptsMessage() {
        seedCompletedOrder()
        val book = OrderBook(ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("tv5_demo", 0))
        book.save("chatDemoRoletest-order", "CUSTOMER")
        val text = "Hello " + java.util.UUID.randomUUID()
        launch(21).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.tv5_message_input).setText(text)
                assertTrue(activity.findViewById<View>(R.id.tv5_send).performClick())
                assertTrue(activity.findViewById<EditText>(R.id.tv5_message_input).text.isEmpty())
            }
        }
        book.save("chatDemoRoletest-order", "SHIPPER")
        launch(21).use {
            onView(withText("Khách hàng")).check(matches(isDisplayed()))
            onView(withText(text)).check(matches(isDisplayed()))
            val rows = book.order("test-order")!!.getJSONArray("chat").objects()
            assertEquals(1, rows.count { it.optString("text") == text && it.optString("role") == "CUSTOMER" })
        }
    }

    @Test fun repeatedInsetsDispatchDoesNotAccumulatePadding() {
        launch(16).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.tv5_root)
                val systemBars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 24, 4, 32))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(root, systemBars)
                val first = listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
                ViewCompat.dispatchApplyWindowInsets(root, systemBars)
                assertEquals(first, listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom))
                val cleared = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE).build()
                ViewCompat.dispatchApplyWindowInsets(root, cleared)
                assertEquals(listOf(0, 0, 0, 0), listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom))
            }
        }
    }

    @Test fun serverOrderMapsDatabaseFieldsOnANewDevice() {
        val row = org.json.JSONObject("""{"id":42,"order_status":"ACCEPTED","subtotal":"138000.00",
            "shipping_fee":"15000.00","discount_amount":"27600.00","total_amount":"125400.00",
            "delivery_address":"UMT","delivery_lat":10.8,"delivery_lng":106.7,
            "qr_verify_token":"demo-token","items":[]}""")
        val order = applyServerOrder(row,org.json.JSONObject())
        assertEquals("42", order.getString("id"))
        assertEquals("ACCEPTED", order.getString("status"))
        assertEquals(125400L, order.getLong("total"))
        assertEquals(15000L, order.getLong("delivery_fee"))
        assertEquals(27600L, order.getLong("discount"))
        assertEquals("UMT", order.getJSONObject("destination").getString("address"))
        assertEquals("demo-token", order.getString("token"))
    }

    @Test fun voucherTotalsUseIntegerDong() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val book = OrderBook(context.getSharedPreferences("test-quote", 0))
        val quote = book.quote(book.sampleCart(), "FOODFLOW20")
        assertEquals(138000L, quote.getLong("subtotal"))
        assertEquals(27600L, quote.getLong("discount"))
        assertEquals(125400L, quote.getLong("total"))
    }
}
