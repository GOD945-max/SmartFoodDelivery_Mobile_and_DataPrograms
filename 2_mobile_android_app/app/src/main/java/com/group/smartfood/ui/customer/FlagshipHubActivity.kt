package com.group.smartfood.ui.customer
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class FlagshipHubActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flagship_hub)
        findViewById<TextView>(R.id.btnBackHub).setOnClickListener{finish()}
        findViewById<LinearLayout>(R.id.bannerRestaurant1).setOnClickListener{
            openBrandMenu("Placeholder Restaurant 1","Placeholder Descriptions")
        }
        findViewById<LinearLayout>(R.id.bannerRestaurant2).setOnClickListener{
            openBrandMenu("Placeholder Restaurant 2","Placeholder Descriptions")
        }
        findViewById<LinearLayout>(R.id.bannerRestaurant3).setOnClickListener{
            openBrandMenu("Placeholder Restaurant 3","Placeholder Descriptions")
        }
        findViewById<LinearLayout>(R.id.bannerRestaurant4).setOnClickListener{
            openBrandMenu("Placeholder Restaurant 4","Placeholder Descriptions")
        }
    }
    private fun openBrandMenu(brandName:String,brandInfo:String){
        val intent=Intent(this,FlagshipMenuActivity::class.java)
        intent.putExtra("BRAND_NAME",brandName)
        intent.putExtra("BRAND_INFO",brandInfo)
        startActivity(intent)
    }
}