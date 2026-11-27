package com.group.smartfood.ui.customer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class AddressActivity:AppCompatActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        val btnBack=findViewById<ImageView>(R.id.btnBack)
        val btnConfirmAddress=findViewById<Button>(R.id.btnConfirmAddress)
        btnBack.setOnClickListener{
            finish()
        }
        btnConfirmAddress.setOnClickListener{
            Toast.makeText(this,"Address selected and shipping fee updated",Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}