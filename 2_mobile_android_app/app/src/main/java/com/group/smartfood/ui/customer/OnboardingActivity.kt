package com.group.smartfood.ui.customer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class OnboardingActivity:AppCompatActivity(){
    private var currentSlide=0
    private val titles=listOf(
        "Fast Deliveries, Better Prices",
        "Group Orders Made Easy",
        "Smart AI Calorie Assistant"
    )
    private val descriptions=listOf(
        "Enjoy your favorite meals with incredible delivery time and discounts",
        "Easily make group orders and automatically split bills",
        "Discover personalized menus and track your daily nutrition with precision"
    )
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        val tvSkip=findViewById<TextView>(R.id.tvSkip)
        val tvTitle=findViewById<TextView>(R.id.tvSlideTitle)
        val tvDesc=findViewById<TextView>(R.id.tvSlideDesc)
        val btnNext=findViewById<Button>(R.id.btnNext)
        val dot1=findViewById<View>(R.id.dot1)
        val dot2=findViewById<View>(R.id.dot2)
        val dot3=findViewById<View>(R.id.dot3)
        fun updateSlide(){
            tvTitle.text=titles[currentSlide]
            tvDesc.text=descriptions[currentSlide]
            val dots=listOf(dot1,dot2,dot3)
            dots.forEachIndexed{index,dot->
                val params=dot.layoutParams
                if (index==currentSlide){
                    params.width=(24*resources.displayMetrics.density).toInt()
                    dot.setBackgroundColor(Color.parseColor("#FF6A13"))
                } else{
                    params.width=(8*resources.displayMetrics.density).toInt()
                    dot.setBackgroundColor(Color.parseColor("#DDDDDD"))
                }
                dot.layoutParams=params
            }
            if (currentSlide==titles.size-1){
                btnNext.text="Start"
                tvSkip.visibility=View.INVISIBLE
            } else {
                btnNext.text="Next"
                tvSkip.visibility=View.VISIBLE
            }
        }
        fun goToAuthScreen() {
            val intent=Intent(this,AuthActivity::class.java)
            startActivity(intent)
            finish()
        }
        btnNext.setOnClickListener {
            if (currentSlide<titles.size-1){
                currentSlide++
                updateSlide()
            } else {
                goToAuthScreen()
            }
        }
        tvSkip.setOnClickListener{
            goToAuthScreen()
        }
        updateSlide()
    }
}