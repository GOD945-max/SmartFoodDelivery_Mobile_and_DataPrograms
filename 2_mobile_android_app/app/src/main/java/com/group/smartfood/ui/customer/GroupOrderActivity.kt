package com.group.smartfood.ui.customer
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import kotlin.random.Random
class GroupOrderActivity:AppCompatActivity(){
    private var selectedMode="Self-pay"
    private var selectedCutoff="12:30"
    private var currentCode="GRP-${Random.nextInt(1000,9999)}"
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_order)
        val tvRoomCode=findViewById<TextView>(R.id.tvRoomCode)
        val btnRegenerateCode=findViewById<TextView>(R.id.btnRegenerateCode)
        val etRoomName=findViewById<EditText>(R.id.etRoomName)
        val spinnerRestaurant=findViewById<Spinner>(R.id.spinnerRestaurant)
        val btnModeSelfPay=findViewById<Button>(R.id.btnModeSelfPay)
        val btnModeHostPays=findViewById<Button>(R.id.btnModeHostPays)
        val tvPaymentModeDesc=findViewById<TextView>(R.id.tvPaymentModeDesc)
        val tvSelectedCutoff=findViewById<TextView>(R.id.tvSelectedCutoff)
        val btnPickCutoffTime=findViewById<Button>(R.id.btnPickCutoffTime)
        val btnCreateGroupRoom=findViewById<Button>(R.id.btnCreateGroupRoom)
        findViewById<TextView>(R.id.btnBackGroupConfig).setOnClickListener{finish()}
        tvRoomCode.text=currentCode
        btnRegenerateCode.setOnClickListener{
            currentCode="GRP-${Random.nextInt(1000,9999)}"
            tvRoomCode.text=currentCode
        }
        val restaurants=listOf("Placeholder Restaurant 1","Placeholder Restaurant 2","Placeholder Restaurant 3","Placeholder Restaurant 4")
        val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,restaurants)
        spinnerRestaurant.adapter=adapter
        fun updateModeUI(){
            if(selectedMode=="Self-pay"){
                btnModeSelfPay.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#FF6A13"))
                btnModeSelfPay.setTextColor(Color.WHITE)
                btnModeHostPays.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                btnModeHostPays.setTextColor(Color.parseColor("#222222"))
                tvPaymentModeDesc.text="Each member pays for their own items after splitting discounts."
            }else{
                btnModeHostPays.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#FF6A13"))
                btnModeHostPays.setTextColor(Color.WHITE)
                btnModeSelfPay.backgroundTintList=ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                btnModeSelfPay.setTextColor(Color.parseColor("#222222"))
                tvPaymentModeDesc.text="Host covers the entire group bill at checkout."
            }
        }
        btnModeSelfPay.setOnClickListener{
            selectedMode="Self-pay"
            updateModeUI()
        }
        btnModeHostPays.setOnClickListener{
            selectedMode="Host pays all"
            updateModeUI()
        }
        btnPickCutoffTime.setOnClickListener{
            val cal=Calendar.getInstance()
            TimePickerDialog(this,{_,hour,minute->
                selectedCutoff=String.format("%02d:%02d",hour,minute)
                tvSelectedCutoff.text="Cutoff at: $selectedCutoff"
            },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show()
        }
        btnCreateGroupRoom.setOnClickListener{
            val roomTitle=etRoomName.text.toString().trim().ifEmpty{"Group Room"}
            GroupOrderManager.roomCode=currentCode
            GroupOrderManager.roomName=roomTitle
            GroupOrderManager.restaurantName=spinnerRestaurant.selectedItem.toString()
            GroupOrderManager.paymentMode=selectedMode
            GroupOrderManager.cutoffTime=selectedCutoff
            GroupOrderManager.initDefaultSampleLobby()
            val intent=Intent(this,GroupLobbyActivity::class.java)
            startActivity(intent)
        }
    }
}