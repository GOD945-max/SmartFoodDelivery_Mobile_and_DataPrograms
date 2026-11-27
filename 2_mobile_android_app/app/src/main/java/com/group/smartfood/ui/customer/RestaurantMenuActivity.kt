package com.group.smartfood.ui.customer
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class RestaurantMenuActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_menu)
        val btnBack=findViewById<ImageView>(R.id.btnBack)
        val txtMenuTitle=findViewById<TextView>(R.id.txtMenuTitle)
        val btnAdd1=findViewById<TextView>(R.id.btnAdd1)
        val btnAdd2=findViewById<TextView>(R.id.btnAdd2)
        btnBack.setOnClickListener{finish()}
        val restaurantName=intent.getStringExtra("RESTAURANT_NAME")?:"Restaurant Menu"
        txtMenuTitle.text=restaurantName
        btnAdd1.setOnClickListener{
            val intent=Intent(this,ItemCustomizationActivity::class.java).apply{
                putExtra("ITEM_NAME","Placeholder Item 1")
                putExtra("ITEM_PRICE","$8.99")
                putExtra("RESTAURANT_NAME",restaurantName)
            }
            startActivity(intent)
        }
        btnAdd2.setOnClickListener{
            val intent=Intent(this, ItemCustomizationActivity::class.java).apply{
                putExtra("ITEM_NAME","Placeholder Item 2")
                putExtra("ITEM_PRICE","$7.49")
                putExtra("RESTAURANT_NAME",restaurantName)
            }
            startActivity(intent)
        }
    }
}