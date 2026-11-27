package com.group.smartfood.ui.customer
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
object CartManager{
    data class CartItem(
        val name:String,
        val price:String,
        val size:String,
        val temp:String,
        val sugar:String,
        var qty:Int=1
    )
    val items=mutableListOf<CartItem>()
    var currentRestaurant:String?=null
}
class CartActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)
        findViewById<TextView>(R.id.btnBackCart).setOnClickListener{finish()}
        renderCartItems()
        findViewById<Button>(R.id.btnPayCart).setOnClickListener{
            if(CartManager.items.isEmpty()){
                Toast.makeText(this,"Cart is empty",Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this,"Transaction Successful",Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun renderCartItems(){
        val container=findViewById<LinearLayout>(R.id.llCartItemsContainer)
        val tvEmpty=findViewById<TextView>(R.id.tvEmptyCart)
        val scrollView=findViewById<ScrollView>(R.id.scrollViewCart)
        container.removeAllViews()
        if(CartManager.items.isEmpty()){
            tvEmpty.visibility=View.VISIBLE
            scrollView.visibility=View.GONE
            findViewById<TextView>(R.id.tvCartTotal).text="$0.00"
            CartManager.currentRestaurant=null
            return
        }
        tvEmpty.visibility=View.GONE
        scrollView.visibility=View.VISIBLE
        var totalPrice=0.0
        for(cartItem in CartManager.items){
            val cleanPrice=cartItem.price.replace("$","").toDoubleOrNull()?:0.0
            totalPrice+=(cleanPrice*cartItem.qty)
            val card=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(28,24,28,24)
                val params=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin=16
                layoutParams=params
            }
            val row=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                layoutParams=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val infoCol=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
            }
            val tvName=TextView(this).apply{
                text=cartItem.name
                textSize=15f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
            }
            val tvDetails=TextView(this).apply{
                text="Size: ${cartItem.size} | ${cartItem.temp} | ${cartItem.sugar}"
                textSize=12f
                setTextColor(Color.parseColor("#777777"))
                setPadding(0,4,0,4)
            }
            val tvPrice=TextView(this).apply{
                text=cartItem.price
                textSize=14f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.parseColor("#FF6A13"))
            }
            infoCol.addView(tvName)
            infoCol.addView(tvDetails)
            infoCol.addView(tvPrice)
            val qtyLayout=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            val currentItem=cartItem
            val btnMinus=Button(this).apply{
                text="-"
                textSize=14f
                setTextColor(Color.parseColor("#222222"))
                backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                layoutParams=LinearLayout.LayoutParams(80,80)
                setOnClickListener{
                    if(currentItem.qty>1){
                        currentItem.qty--
                    }else{
                        CartManager.items.remove(currentItem)
                    }
                    renderCartItems()
                }
            }
            val tvQty=TextView(this).apply{
                text=currentItem.qty.toString()
                textSize=15f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
                gravity=Gravity.CENTER
                layoutParams=LinearLayout.LayoutParams(70,LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val btnPlus=Button(this).apply{
                text="+"
                textSize=14f
                setTextColor(Color.WHITE)
                backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6A13"))
                layoutParams=LinearLayout.LayoutParams(80,80)
                setOnClickListener{
                    currentItem.qty++
                    renderCartItems()
                }
            }
            qtyLayout.addView(btnMinus)
            qtyLayout.addView(tvQty)
            qtyLayout.addView(btnPlus)
            row.addView(infoCol)
            row.addView(qtyLayout)
            card.addView(row)
            container.addView(card)
        }
        findViewById<TextView>(R.id.tvCartTotal).text=String.format("$%.2f",totalPrice)
    }
}
