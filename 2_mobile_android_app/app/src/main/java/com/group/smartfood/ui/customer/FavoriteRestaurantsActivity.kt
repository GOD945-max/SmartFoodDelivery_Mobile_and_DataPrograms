package com.group.smartfood.ui.customer
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
class FavoriteRestaurantsActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_restaurants)
        val btnBack=findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener{finish()}
        setupMenuClick(R.id.itemRestaurant1,"Placeholder Restaurant 1")
        setupMenuClick(R.id.itemRestaurant2,"Placeholder Restaurant 2")
        setupMenuClick(R.id.itemRestaurant3,"Placeholder Restaurant 3")
    }
    private fun setupMenuClick(itemId:Int,restaurantName:String){
        val item=findViewById<LinearLayout>(itemId)
        item.setOnClickListener{
            val intent=Intent(this,RestaurantMenuActivity::class.java)
            intent.putExtra("RESTAURANT_NAME",restaurantName)
            startActivity(intent)
        }
    }
}