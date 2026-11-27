package com.group.smartfood.ui.customer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
class GroupLobbyActivity:AppCompatActivity(){
    private lateinit var containerLobbyMembers:LinearLayout
    private lateinit var tvMemberCountHeader:TextView
    private lateinit var tvLobbyTotalAmount:TextView
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_lobby)

        if(GroupOrderManager.memberOrders.isEmpty()){
            GroupOrderManager.initDefaultSampleLobby()
        }
        val btnBackLobby=findViewById<TextView>(R.id.btnBackLobby)
        val tvLobbyRoomName=findViewById<TextView>(R.id.tvLobbyRoomName)
        val tvLobbyRestaurant=findViewById<TextView>(R.id.tvLobbyRestaurant)
        val btnShareRoomCode=findViewById<TextView>(R.id.btnShareRoomCode)
        val tvLobbyRoomCode=findViewById<TextView>(R.id.tvLobbyRoomCode)
        val tvLobbyCutoff=findViewById<TextView>(R.id.tvLobbyCutoff)
        val tvLobbyPaymentBadge=findViewById<TextView>(R.id.tvLobbyPaymentBadge)
        val btnSimulateJoin=findViewById<TextView>(R.id.btnSimulateJoin)
        val btnAddMyItems=findViewById<Button>(R.id.btnAddMyItems)
        val btnLockAndCheckout=findViewById<Button>(R.id.btnLockAndCheckout)
        containerLobbyMembers=findViewById(R.id.containerLobbyMembers)
        tvMemberCountHeader=findViewById(R.id.tvMemberCountHeader)
        tvLobbyTotalAmount=findViewById(R.id.tvLobbyTotalAmount)
        tvLobbyRoomName.text=GroupOrderManager.roomName
        tvLobbyRestaurant.text=GroupOrderManager.restaurantName
        tvLobbyRoomCode.text="CODE: ${GroupOrderManager.roomCode}"
        tvLobbyCutoff.text="Cutoff: ${GroupOrderManager.cutoffTime}"
        tvLobbyPaymentBadge.text="Payment Mode: ${GroupOrderManager.paymentMode}"
        btnBackLobby.setOnClickListener{
            finish()
        }
        btnShareRoomCode.setOnClickListener{
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip=ClipData.newPlainText("Group Order Code",GroupOrderManager.roomCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this,"Copied room code: ${GroupOrderManager.roomCode}",Toast.LENGTH_SHORT).show()
        }
        btnSimulateJoin.setOnClickListener{
            GroupOrderManager.addPlaceholderMember()
            renderMembers()
            Toast.makeText(this,"Added placeholder member!",Toast.LENGTH_SHORT).show()
        }
        btnAddMyItems.setOnClickListener{
            val intent=Intent(this,RestaurantMenuActivity::class.java)
            intent.putExtra("RESTAURANT_NAME",GroupOrderManager.restaurantName)
            intent.putExtra("IS_GROUP_ORDER",true)
            startActivity(intent)
        }
        btnLockAndCheckout.setOnClickListener{
            val intent=Intent(this,CartActivity::class.java)
            intent.putExtra("IS_GROUP_ORDER",true)
            startActivity(intent)
        }
        renderMembers()
    }
    override fun onResume(){
        super.onResume()
        renderMembers()
    }
    private fun renderMembers(){
        containerLobbyMembers.removeAllViews()
        val orders=GroupOrderManager.memberOrders
        tvMemberCountHeader.text="MEMBERS & ITEMS (${orders.size})"
        for(order in orders){
            val card=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(36,30,36,30)
                val params=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin=24
                layoutParams=params
            }
            val topRow=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                layoutParams=LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val tvName=TextView(this).apply{
                text=if(order.isHost) "${order.memberName} ★" else order.memberName
                setTextColor(Color.parseColor("#222222"))
                textSize=15f
                setTypeface(null,Typeface.BOLD)
                layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
            }
            val tvPrice=TextView(this).apply{
                text="$${String.format(Locale.US,"%.2f",order.price)}"
                setTextColor(Color.parseColor("#FF6A13"))
                textSize=14f
                setTypeface(null,Typeface.BOLD)
            }
            topRow.addView(tvName)
            topRow.addView(tvPrice)
            val tvItem=TextView(this).apply{
                text=order.itemName
                setTextColor(Color.parseColor("#333333"))
                textSize=13f
                setTypeface(null,Typeface.BOLD)
                setPadding(0,10,0,0)
            }
            val tvCustom=TextView(this).apply{
                text=order.customization
                setTextColor(Color.parseColor("#777777"))
                textSize=12f
                setPadding(0,4,0,0)
            }
            card.addView(topRow)
            card.addView(tvItem)
            card.addView(tvCustom)
            containerLobbyMembers.addView(card)
        }
        tvLobbyTotalAmount.text="$${String.format(Locale.US,"%.2f",GroupOrderManager.getTotalAmount())}"
    }
}