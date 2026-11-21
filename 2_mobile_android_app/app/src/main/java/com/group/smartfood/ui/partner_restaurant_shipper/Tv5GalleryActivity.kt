package com.group.smartfood.tv5

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.group.smartfood.R

class Tv5GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv5_activity_gallery)
        applyTv5Insets(findViewById(R.id.tv5_root))
        val screenButtons = listOf(
            R.id.tv5_open_16 to 16,
            R.id.tv5_open_17 to 17,
            R.id.tv5_open_18 to 18,
            R.id.tv5_open_19 to 19,
            R.id.tv5_open_20 to 20,
            R.id.tv5_open_21 to 21,
            R.id.tv5_open_22 to 22,
            R.id.tv5_open_23 to 23,
            R.id.tv5_open_24 to 24,
            R.id.tv5_open_25 to 25,
            R.id.tv5_open_26 to 26,
            R.id.tv5_open_27 to 27,
            R.id.tv5_open_28 to 28,
            R.id.tv5_open_29 to 29,
            R.id.tv5_open_30 to 30
        )
        screenButtons.forEach { (viewId, number) ->
            findViewById<View>(viewId).setOnClickListener {
                startActivity(Intent(this, Tv5ScreenActivity::class.java)
                    .putExtra(Tv5ScreenActivity.EXTRA_SCREEN, number))
            }
        }
    }
}
