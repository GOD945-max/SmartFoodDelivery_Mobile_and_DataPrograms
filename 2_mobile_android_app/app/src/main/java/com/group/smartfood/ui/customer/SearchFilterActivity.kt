package com.group.smartfood.ui.customer
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class SearchFilterActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_filter)
        val btnBack=findViewById<ImageView>(R.id.btnBack)
        val btnApplyFilter=findViewById<Button>(R.id.btnApplyFilter)
        val calorieGroup=listOf(
            findViewById<Button>(R.id.btnCalorieUnder400),
            findViewById<Button>(R.id.btnCalorie400to600)
        )
        val sortGroup=listOf(
            findViewById<Button>(R.id.btnSortPopular),
            findViewById<Button>(R.id.btnSortNearest),
            findViewById<Button>(R.id.btnSortTopRated)
        )
        val dietGroup=listOf(
            findViewById<Button>(R.id.btnDietVegan),
            findViewById<Button>(R.id.btnDietKeto),
            findViewById<Button>(R.id.btnDietLowCarb)
        )
        val btnPerkFreeShipping=findViewById<Button>(R.id.btnPerkFreeShipping)
        var isFreeShippingSelected=false
        val btnPerkDiscount=findViewById<Button>(R.id.btnPerkDiscount)
        var isDiscountSelected=false
        fun setupToggleGroup(group:List<Button>){
            for(button in group){
                button.setOnClickListener{
                    for(b in group){
                        b.setBackgroundColor(Color.parseColor("#EEEEEE"))
                        b.setTextColor(Color.parseColor("#444444"))
                    }
                    button.setBackgroundColor(Color.parseColor("#FF6A13"))
                    button.setTextColor(Color.parseColor("#FFFFFF"))
                }
            }
        }
        setupToggleGroup(calorieGroup)
        setupToggleGroup(sortGroup)
        setupToggleGroup(dietGroup)
        btnPerkFreeShipping.setOnClickListener{
            isFreeShippingSelected=!isFreeShippingSelected
            if(isFreeShippingSelected){
                btnPerkFreeShipping.setBackgroundColor(Color.parseColor("#FF6A13"))
                btnPerkFreeShipping.setTextColor(Color.parseColor("#FFFFFF"))
            }else{
                btnPerkFreeShipping.setBackgroundColor(Color.parseColor("#EEEEEE"))
                btnPerkFreeShipping.setTextColor(Color.parseColor("#444444"))
            }
        }
        btnPerkDiscount.setOnClickListener{
            isDiscountSelected=!isDiscountSelected
            if(isDiscountSelected){
                btnPerkDiscount.setBackgroundColor(Color.parseColor("#FF6A13"))
                btnPerkDiscount.setTextColor(Color.parseColor("#FFFFFF"))
            }else{
                btnPerkDiscount.setBackgroundColor(Color.parseColor("#EEEEEE"))
                btnPerkDiscount.setTextColor(Color.parseColor("#444444"))
            }
        }
        btnBack.setOnClickListener{
            finish()
        }
        btnApplyFilter.setOnClickListener{
            Toast.makeText(this,"Filters applied successfully!",Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}