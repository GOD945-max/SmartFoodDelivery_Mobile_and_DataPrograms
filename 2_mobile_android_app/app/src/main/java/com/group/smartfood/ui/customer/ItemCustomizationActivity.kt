package com.group.smartfood.ui.customer
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
class ItemCustomizationActivity:AppCompatActivity(){
    private var selectedSize="S"
    private var selectedTemp="Iced"
    private var selectedSugar="Sugar"
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_customization)
        val itemName=intent.getStringExtra("ITEM_NAME")?:"Placeholder Item"
        val itemPrice=intent.getStringExtra("ITEM_PRICE")?:"$0.00"
        val restaurantName=intent.getStringExtra("RESTAURANT_NAME")?:"Restaurant"
        findViewById<TextView>(R.id.tvCustomItemName).text=itemName
        findViewById<TextView>(R.id.tvCustomItemPrice).text=itemPrice
        findViewById<TextView>(R.id.btnCloseCustomization).setOnClickListener{finish()}
        val btnS=findViewById<Button>(R.id.btnSizeS)
        val btnM=findViewById<Button>(R.id.btnSizeM)
        val btnL=findViewById<Button>(R.id.btnSizeL)
        val btnXL=findViewById<Button>(R.id.btnSizeXL)
        val sizeButtons=listOf(btnS,btnM,btnL,btnXL)
        btnS.setOnClickListener{selectOption(btnS,sizeButtons);selectedSize="S"}
        btnM.setOnClickListener{selectOption(btnM,sizeButtons);selectedSize="M"}
        btnL.setOnClickListener{selectOption(btnL,sizeButtons);selectedSize="L"}
        btnXL.setOnClickListener{selectOption(btnXL,sizeButtons);selectedSize="XL"}
        val btnIced=findViewById<Button>(R.id.btnTempIced)
        val btnHot=findViewById<Button>(R.id.btnTempHot)
        val tempButtons=listOf(btnIced,btnHot)
        btnIced.setOnClickListener{selectOption(btnIced,tempButtons);selectedTemp="Iced"}
        btnHot.setOnClickListener{selectOption(btnHot,tempButtons);selectedTemp="Hot"}
        val btnSugar=findViewById<Button>(R.id.btnSugarYes)
        val btnNoSugar=findViewById<Button>(R.id.btnSugarNo)
        val sugarButtons=listOf(btnSugar,btnNoSugar)
        btnSugar.setOnClickListener{selectOption(btnSugar,sugarButtons);selectedSugar="Sugar"}
        btnNoSugar.setOnClickListener{selectOption(btnNoSugar,sugarButtons);selectedSugar="No Sugar"}
        findViewById<Button>(R.id.btnDoneCustomization).setOnClickListener{
            if(CartManager.items.isNotEmpty()&&CartManager.currentRestaurant!=null&&CartManager.currentRestaurant!=restaurantName){
                AlertDialog.Builder(this)
                    .setTitle("Start New Cart?")
                    .setMessage("Your cart contains items from ${CartManager.currentRestaurant}. Adding items from $restaurantName will clear your current cart.")
                    .setPositiveButton("Clear & Add"){_,_->
                        CartManager.items.clear()
                        CartManager.currentRestaurant=restaurantName
                        saveItemAndOpenCart(itemName,itemPrice)
                    }
                    .setNegativeButton("Cancel",null)
                    .show()
            }else{
                if(CartManager.currentRestaurant==null){
                    CartManager.currentRestaurant=restaurantName
                }
                saveItemAndOpenCart(itemName,itemPrice)
            }
        }
    }
    private fun saveItemAndOpenCart(name:String,price:String){
        CartManager.items.add(CartManager.CartItem(name,price,selectedSize,selectedTemp,selectedSugar))
        Toast.makeText(this,"Added $name to Cart!",Toast.LENGTH_SHORT).show()

        val intent=Intent(this,CartActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun selectOption(selected:Button,allButtons:List<Button>){
        for(btn in allButtons){
            btn.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            btn.setTextColor(Color.parseColor("#444444"))
        }
        selected.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#FF6A13"))
        selected.setTextColor(Color.WHITE)
    }
}