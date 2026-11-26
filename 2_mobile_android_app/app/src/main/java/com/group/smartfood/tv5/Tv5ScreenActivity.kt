package com.group.smartfood.tv5

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.group.smartfood.R

class Tv5ScreenActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCREEN = "tv5_screen_number"
    }

    private lateinit var flow: CustomerFlow

    override fun onResume() { super.onResume(); flow.render() }
    override fun onPause() { flow.pause(); super.onPause() }
    override fun onDestroy() { flow.close(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = when (intent.getIntExtra(EXTRA_SCREEN, 16)) {
            16 -> R.layout.tv5_scr_16_checkout
            17 -> R.layout.tv5_scr_17_vouchers
            18 -> R.layout.tv5_scr_18_payment
            19 -> R.layout.tv5_scr_19_success
            20 -> R.layout.tv5_scr_20_tracking
            21 -> R.layout.tv5_scr_21_chat
            22 -> R.layout.tv5_scr_22_order_details
            23 -> R.layout.tv5_scr_23_review
            24 -> R.layout.tv5_scr_24_orders
            25 -> R.layout.tv5_scr_25_receipt
            26 -> R.layout.tv5_scr_26_profile
            27 -> R.layout.tv5_scr_27_loyalty
            28 -> R.layout.tv5_scr_28_addresses
            29 -> R.layout.tv5_scr_29_notifications
            30 -> R.layout.tv5_scr_30_help_settings
            else -> R.layout.tv5_scr_16_checkout
        }
        setContentView(layout)
        flow = CustomerFlow(this, intent.getIntExtra(EXTRA_SCREEN, 16).takeIf { it in 16..30 } ?: 16)
        applyTv5Insets(findViewById(R.id.tv5_root))
        findViewById<View>(R.id.tv5_back).setOnClickListener { finish() }
    }
}
