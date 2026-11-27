package com.group.smartfood.ui.customer
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class FlagshipMenuActivity:AppCompatActivity(){
    data class MenuItem(val name:String,val desc:String,val price:String)
    private val popularItems=listOf(
        MenuItem("Placeholder Item 1","Placeholder Description","$4.99"),
        MenuItem("Placeholder Item 2","Placeholder Description","$6.49"),
        MenuItem("Placeholder Item 3","Placeholder Description","$5.29"),
        MenuItem("Placeholder Item 4","Placeholder Description","$3.99"),
        MenuItem("Placeholder Item 5","Placeholder Description","$2.99"),
        MenuItem("Placeholder Item 6","Placeholder Description","$7.99")
    )
    private val signatureItems=listOf(
        MenuItem("Placeholder Signature 1","Placeholder Description","$9.99"),
        MenuItem("Placeholder Signature 2","Placeholder Description","$11.49"),
        MenuItem("Placeholder Signature 3","Placeholder Description","$10.29")
    )
    private val comboItems=listOf(
        MenuItem("Placeholder Combo 1","Placeholder Description","$12.99"),
        MenuItem("Placeholder Combo 2","Placeholder Description","$18.50"),
        MenuItem("Placeholder Combo 3","Placeholder Description","$14.25")
    )
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flagship_menu)
        val brandName=intent.getStringExtra("BRAND_NAME")?:"Placeholder Restaurant"
        val brandInfo=intent.getStringExtra("BRAND_INFO")?:"Placeholder Descriptions"
        findViewById<TextView>(R.id.tvMenuBrandName).text=brandName
        findViewById<TextView>(R.id.tvMenuBrandHours).text=brandInfo
        findViewById<TextView>(R.id.btnBackMenu).setOnClickListener{finish()}
        renderMenuItems(popularItems)
        val tabPopular=findViewById<Button>(R.id.tabPopular)
        val tabSignature=findViewById<Button>(R.id.tabSignature)
        val tabCombos=findViewById<Button>(R.id.tabCombos)

        tabPopular.setOnClickListener{
            highlightTab(tabPopular,tabSignature,tabCombos)
            renderMenuItems(popularItems)
        }
        tabSignature.setOnClickListener{
            highlightTab(tabSignature,tabPopular,tabCombos)
            renderMenuItems(signatureItems)
        }
        tabCombos.setOnClickListener{
            highlightTab(tabCombos,tabPopular,tabSignature)
            renderMenuItems(comboItems)
        }
    }
    private fun highlightTab(active:Button,other1:Button,other2:Button){
        active.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#FF6A13"))
        active.setTextColor(Color.WHITE)
        listOf(other1,other2).forEach{
            it.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            it.setTextColor(Color.parseColor("#444444"))
        }
    }
    private fun renderMenuItems(items:List<MenuItem>){
        val container=findViewById<LinearLayout>(R.id.llMenuItemsContainer)
        container.removeAllViews()
        for(item in items){
            val row=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(36,32,36,32)
                val params=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin=24
                layoutParams=params
            }
            val infoCol=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
            }
            val tvName=TextView(this).apply{
                text=item.name
                textSize=15f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
            }
            val tvDesc=TextView(this).apply{
                text=item.desc
                textSize=12f
                setTextColor(Color.parseColor("#777777"))
                setPadding(0,6,16,8)
            }
            val tvPrice=TextView(this).apply{
                text=item.price
                textSize=14f
                setTypeface(null,Typeface.BOLD)
                setTextColor(Color.parseColor("#FF6A13"))
            }
            infoCol.addView(tvName)
            infoCol.addView(tvDesc)
            infoCol.addView(tvPrice)
            val currentItem=item
            val btnAdd=Button(this).apply{
                text="+"
                textSize=18f
                setTextColor(Color.WHITE)
                backgroundTintList=ColorStateList.valueOf(Color.parseColor("#FF6A13"))
                layoutParams=LinearLayout.LayoutParams(110,110)
                setOnClickListener{
                    val intent=Intent(this@FlagshipMenuActivity,ItemCustomizationActivity::class.java)
                    intent.putExtra("ITEM_NAME",currentItem.name)
                    intent.putExtra("ITEM_PRICE",currentItem.price)
                    startActivity(intent)
                }
            }
            row.addView(infoCol)
            row.addView(btnAdd)
            container.addView(row)
        }
    }
}