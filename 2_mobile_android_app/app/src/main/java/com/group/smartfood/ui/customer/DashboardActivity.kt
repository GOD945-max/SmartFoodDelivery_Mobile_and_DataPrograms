package com.group.smartfood.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val layoutDeliverTo=findViewById<LinearLayout>(R.id.layoutDeliverTo)
        layoutDeliverTo.setOnClickListener{
            val intent=Intent(this,AddressActivity::class.java)
            startActivity(intent)
        }
        val searchBarStub=findViewById<EditText>(R.id.searchBarStub)
        searchBarStub.setOnClickListener{
            val intent=Intent(this,SearchFilterActivity::class.java)
            startActivity(intent)
        }
        val bannerFlipper=findViewById<ViewFlipper>(R.id.bannerFlipper)
        val bannerFavoriteShortcut=findViewById<LinearLayout>(R.id.bannerFavoriteShortcut)
        bannerFavoriteShortcut.setOnClickListener{
            val intent=Intent(this,FavoriteRestaurantsActivity::class.java)
            startActivity(intent)
        }
        val btnNextBanner=findViewById<TextView>(R.id.btnNextBanner)
        btnNextBanner.setOnClickListener{
            bannerFlipper.inAnimation=AnimationUtils.loadAnimation(this,android.R.anim.slide_in_left)
            bannerFlipper.outAnimation=AnimationUtils.loadAnimation(this,android.R.anim.slide_out_right)
            bannerFlipper.showNext()
        }
        val btnFlagshipHub=findViewById<LinearLayout>(R.id.btnFlagshipHub)
        btnFlagshipHub.setOnClickListener{
            val intent=Intent(this,FlagshipHubActivity::class.java)
            startActivity(intent)
        }
        val btnGroupOrderRoom=findViewById<LinearLayout>(R.id.btnGroupOrderRoom)
        btnGroupOrderRoom.setOnClickListener{
            startActivity(Intent(this,GroupOrderActivity::class.java))
        }
    }
}