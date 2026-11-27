package com.group.smartfood.ui.customer
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class AuthActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        val etPhone=findViewById<EditText>(R.id.etPhone)
        val btnGetOtp=findViewById<Button>(R.id.btnGetOtp)
        btnGetOtp.setOnClickListener{
            val phone=etPhone.text.toString().trim()
            if (phone.length>=9){
                Toast.makeText(this, "OTP sent to $phone",Toast.LENGTH_SHORT).show()
                val intent=Intent(this,OtpActivity::class.java)
                intent.putExtra("PHONE_NUMBER",phone)
                startActivity(intent)
            } else {
                etPhone.error="Please enter a valid phone number"
            }
        }
    }
}