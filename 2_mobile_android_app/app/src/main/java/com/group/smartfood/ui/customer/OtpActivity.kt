package com.group.smartfood.ui.customer
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class OtpActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)
        val tvSubtitle=findViewById<TextView>(R.id.tvOtpSubtitle)
        val etOtpCode=findViewById<EditText>(R.id.etOtpCode)
        val btnVerify=findViewById<Button>(R.id.btnVerify)
        val phone=intent.getStringExtra("PHONE_NUMBER")?:""
        if (phone.isNotEmpty()){
            tvSubtitle.text="Code sent to $phone\n (use 1234)"
        }
        btnVerify.setOnClickListener{
            val code=etOtpCode.text.toString().trim()
            if (code=="1234"){
                Toast.makeText(this,"Verification Successful!",Toast.LENGTH_SHORT).show()
                val sharedPrefs=getSharedPreferences("AppPrefs",MODE_PRIVATE)
                sharedPrefs.edit().putBoolean("isLoggedIn",true).apply()
                val intent=Intent(this,DashboardActivity::class.java)
                intent.flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}