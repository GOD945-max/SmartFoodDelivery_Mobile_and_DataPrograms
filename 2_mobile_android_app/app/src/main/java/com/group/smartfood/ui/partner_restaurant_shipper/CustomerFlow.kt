package com.group.smartfood.tv5

import androidx.core.widget.doAfterTextChanged
import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.location.*
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.group.smartfood.R
import com.group.smartfood.data.api.*
import org.json.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Call
import okhttp3.ResponseBody
import java.util.UUID
import java.util.concurrent.Executors

internal class CustomerFlow(private val a: Tv5ScreenActivity, private val screen: Int) {
    private val intent = a.intent
    private val live = intent.hasExtra("api_base_url")
    private val book = OrderBook(a.getSharedPreferences(if (live) "tv5_user_" + intent.getIntExtra("customer_id",0) else "tv5_demo",0))
    private val api by lazy { RetrofitClient.create(intent.getStringExtra("api_base_url").orEmpty(), intent.getStringExtra("jwt").orEmpty()) }
    private val cart get() = intent.getStringExtra("cart_json")?.let(::JSONObject) ?: book.sampleCart()
    private val draft get() = book.quote(cart, book.value("voucher", if(live) "" else "SMART20"))
    private val id get() = intent.getStringExtra("order_id")?.takeIf { it.isNotBlank() } ?: book.orders().firstOrNull()?.optString("id").orEmpty()
    private val order get() = book.order(id)
    private val scroll = a.findViewById<NestedScrollView>(R.id.tv5_scroll)
    private val body = scroll.getChildAt(0) as LinearLayout
    private val footer = a.findViewById<LinearLayout>(R.id.tv5_footer)
    private val jobs = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var map: MapView? = null
    private var listener: LocationListener? = null
    private val locations = a.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var busy = false
    private var active = false
    private val permission = a.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(it.values.any { allowed -> allowed }) locate() else fallback("Chưa cấp quyền vị trí")
    }
    private val selectQr = a.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            a.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            book.save("momo_image",uri.toString())
            render()
        }
    }
    private val receipt = a.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val o = order
        if(uri != null && o != null) runCatching {
            a.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(receiptText(o)) }
        }.onFailure { message("Không lưu được biên nhận") }
    }
    private fun dp(n:Int) = (n*a.resources.displayMetrics.density+0.5f).toInt()
    private fun message(s:String) = Toast.makeText(a,s,Toast.LENGTH_LONG).show()
    private fun label(text:String, parent:LinearLayout=body, size:Float=16f): TextView = TextView(a).apply {
        this.text = text
        textSize = size
        setTextColor(a.getColor(R.color.tv5_ink))
        setPadding(dp(6),dp(9),dp(6),dp(9))
        parent.addView(this)
    }
    private fun card(title:String, detail:String=""): LinearLayout {
        val box=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(5),dp(12),dp(8))
            setBackgroundResource(R.drawable.tv5_bg_card)
        }
        body.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        label(title,box,18f).setTypeface(null,Typeface.BOLD)
        if(detail.isNotEmpty()) label(detail,box,14f)
        return box
    }
    private fun button(text:String,parent:LinearLayout=body, action:()->Unit) = MaterialButton(a).apply {
        this.text=text
        isAllCaps=false
        parent.addView(this,LinearLayout.LayoutParams(-1,-2))
        setOnClickListener { action() }
    }
    private fun field(hint:String, initial:String="",parent:LinearLayout=body) = EditText(a).apply {
        this.hint=hint;setText(initial);parent.addView(this)
    }
    private fun prompt(title:String, initial:String="", done:(String)->Unit) {
        val input=EditText(a).apply { setText(initial);setSingleLine() }
        AlertDialog.Builder(a).setTitle(title).setView(input).setNegativeButton("Đóng",null)
            .setPositiveButton("Lưu") { _,_->input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done) }.show()
    }
    private fun go(number:Int,orderId:String=id,finish:Boolean=false) {
        val next=Intent(intent).setClass(a,Tv5ScreenActivity::class.java)
            .putExtra(Tv5ScreenActivity.EXTRA_SCREEN,number).putExtra("order_id",orderId)
        a.startActivity(next)
        if(finish) a.finish()
    }
    private fun nav() {
            footer.orientation = LinearLayout.HORIZONTAL

            val tabs = listOf(
                "Đặt món" to 16,
                "Đơn hàng" to 24,
                "Tin nhắn" to 21,
                "Hồ sơ" to 26
            )

            for ((name, target) in tabs) {
                val tab = button(name, footer) {
                    if (target != screen) go(target, finish = true)
                }

                tab.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                tab.minWidth = 0
                tab.minimumWidth = 0
                tab.setPadding(dp(2), 0, dp(2), 0)
                tab.textSize = 12f
        }
    }
    fun render() {
        active=true
        map?.onDetach(); map=null
        body.removeAllViews();footer.removeAllViews();footer.orientation=LinearLayout.VERTICAL
        try {
            when(screen) {
                16->checkout()
                17->vouchers()
                18->payment()
                24->orders()
                26->profile()
                27->loyalty()
                28->addresses()
                29->notifications()
                30->settings()
                else->{
                    val o=order
                    if(o==null) { card("Chưa có đơn hàng");button("Đến thanh toán",footer){go(16)} }
                    else when(screen) {
                        19->{card("Đã tạo đơn " + o.getString("id"),status(o));summary(o);button("Theo dõi đơn",footer){go(20)};button("Chi tiết đơn"){go(22)}}
                        20->tracking(o)
                        21->chat(o)
                        22->details(o)
                        23->review(o)
                        25->{card("Biên nhận",receiptText(o));button("Lưu biên nhận",footer){receipt.launch("SmartFood-"+id+".txt")}}
                    }
                }
            }
        } catch(e:Exception) { card("Không đọc được dữ liệu", e.localizedMessage ?: "Kiểm tra lại giỏ hàng.") }
    }
    fun pause(){ active=false; stopLocation();map?.onPause() }
    fun close(){ pause();map?.onDetach();jobs.shutdownNow() }
    private fun summary(o:JSONObject) {
        val group=card(o.optString("restaurant_name","Đơn hàng"))
        o.getJSONArray("items").objects().forEach {
            label(it.optString("name","Món "+it.optString("item_id"))+" × "+it.getInt("quantity")+"   "+dong(it.getLong("unit_price")*it.getInt("quantity")),group)
        }
        label("Tiền món   "+dong(o.getLong("subtotal"))+"\nPhí giao   "+dong(o.getLong("delivery_fee"))+"\nGiảm giá   −"+dong(o.getLong("discount")),group)
        label("Tổng cộng   "+dong(o.getLong("total")),group,20f)
    }
    private fun status(o:JSONObject) = when(o.optString("status")) {
        "PENDING"->"Chờ quán xác nhận"
        "CONFIRMED"->"Quán đã xác nhận"
        "PREPARING"->"Đang chuẩn bị món"
        "DELIVERING"->"Đang giao"
        "COMPLETED"->"Đã giao"
        "CANCELLED"->"Đã hủy"
        else->o.optString("status")
    }
    private fun checkout(){
        if(live && (!intent.hasExtra("cart_json") || intent.getIntExtra("customer_id",0)<=0 || intent.getStringExtra("jwt").isNullOrBlank())){
            card("Thiếu giỏ hàng hoặc phiên đăng nhập từ SCR-15");return
        }
        val o=draft
        val layout=a.layoutInflater.inflate(R.layout.tv5_scr_16_checkout, null)
        val template=layout.findViewById<NestedScrollView>(R.id.tv5_scroll).getChildAt(0) as LinearLayout
        while(template.childCount>0) {
            val view=template.getChildAt(0)
            template.removeViewAt(0)
            body.addView(view)
        }
        fun slot(name:String)=body.findViewWithTag<TextView>(name)
        slot("customer").text=book.value("profile","Khoa")
        slot("address").text=book.address().getString("address")
        slot("change").setOnClickListener{go(28)}
        val schedule=book.value("schedule","Giao ngay")
        slot("now").text=if(schedule=="Giao ngay") "Giao ngay ✓" else "Giao ngay"
        slot("now").setOnClickListener{book.save("schedule","Giao ngay");render()}
        slot("schedule").text=if(schedule=="Giao ngay") "Hẹn giờ" else schedule
        if(live) (slot("schedule").parent as View).visibility=View.GONE
        else slot("schedule").setOnClickListener{
            val cal=java.util.Calendar.getInstance()
            android.app.TimePickerDialog(a,{_,hour,minute->
                val time=String.format(java.util.Locale.ROOT,"%02d:%02d",hour,minute)
                book.save("schedule","Hẹn "+time);render()
            },cal.get(java.util.Calendar.HOUR_OF_DAY),cal.get(java.util.Calendar.MINUTE),true).show()
        }
        slot("restaurant").text=o.optString("restaurant_name","Nhà hàng")
        val itemRow=slot("firstItem").parent.parent as LinearLayout
        val itemCard=itemRow.parent as LinearLayout
        while(itemCard.childCount>1)itemCard.removeViewAt(1)
        for((index,item) in o.getJSONArray("items").objects().withIndex()){
            val row=LinearLayout(a).apply{gravity=Gravity.CENTER_VERTICAL}
            itemCard.addView(row)
            row.addView(ImageView(a).apply{setImageResource(if(index==1)R.drawable.tv5_ic_smoothie else R.drawable.tv5_ic_bowl)},LinearLayout.LayoutParams(dp(44),dp(44)))
            val name=label(item.optString("name")+" × "+item.getInt("quantity"),row)
            name.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
            label(dong(item.getLong("unit_price")*item.getInt("quantity")),row,14f)
        }
        slot("voucher").text=o.optString("voucher_code").ifBlank{"Chọn voucher"}
        (slot("voucher").parent as View).setOnClickListener{go(17)}
        for((tag,key) in listOf("subtotal" to "subtotal","fee" to "delivery_fee","discount" to "discount","total" to "total")){
            slot(tag).text=(if(tag=="discount") "−" else "")+dong(o.getLong(key))
        }
        slot("estimate").text=if(live)"Tổng tạm tính • chốt theo kết quả máy chủ" else "Giỏ demo • giá bằng VNĐ"
        button("Chọn thanh toán · "+dong(o.getLong("total")),footer){go(18,"")}
        button("Đơn hàng của tôi"){go(24)}
        button("Hồ sơ"){go(26)}
    }
    private fun vouchers(){
        val list=if(live) cart.optJSONArray("vouchers")?.let { v->(0 until v.length()).map{v.getString(it)} }.orEmpty() else listOf("SMART20","FREESHIP")
        for(code in list + "") button((if(code.isEmpty()) "Bỏ voucher" else code) + (if(code==draft.optString("voucher_code")) " ✓" else "")){
            book.save("voucher",code);a.finish()
        }
        if(live)label("Mức giảm cuối cùng do máy chủ tính khi đặt đơn.")
    }
    private fun receiptText(o:JSONObject):String = buildString {
        appendLine("SmartFood • "+o.getString("id"));appendLine(status(o))
        o.getJSONArray("items").objects().forEach { appendLine(it.optString("name")+" × "+it.getInt("quantity")+"  "+dong(it.getLong("unit_price")*it.getInt("quantity"))) }
        appendLine("Tiền món: "+dong(o.getLong("subtotal")));appendLine("Phí giao: "+dong(o.getLong("delivery_fee")))
        appendLine("Giảm: "+dong(o.getLong("discount")));appendLine("Tổng đơn: "+dong(o.getLong("total")))
        appendLine(o.optString("method")+" • "+o.optString("payment"));append(o.getJSONObject("destination").getString("address"))
    }

    private fun runApi(call:Call<ResponseBody>,onSuccess:(JSONObject)->Unit) {
        if(busy)return
        busy=true
        jobs.execute {
            val result=runCatching {
                val response=call.execute()
                if(!response.isSuccessful) {
                    val error=response.errorBody()?.use { it.string() }.orEmpty()
                    throw IllegalStateException(runCatching{JSONObject(error).getString("message")}.getOrDefault("HTTP "+response.code()))
                }
                val raw=response.body()?.use { it.string() }.orEmpty()
                val json=if(raw.isBlank()) JSONObject() else JSONObject(raw)
                json.optJSONObject("data") ?: json
            }
            a.runOnUiThread {
                busy=false
                if(!a.isDestroyed && !a.isFinishing) result.onSuccess { runCatching { onSuccess(it) }.onFailure { e->message(e.message ?: "Phản hồi chưa hợp lệ") } }
                    .onFailure { message(it.message ?: "Không kết nối được máy chủ") }
            }
        }
    }
    private fun payment(){
        val locked=intent.getStringExtra("order_id")?.takeIf { it.isNotBlank() }?.let { book.order(it) }
        if(locked!=null){ showPayment(locked);return }
        val q=draft
        val template=a.layoutInflater.inflate(R.layout.tv5_scr_18_payment,null)
        val source=template.findViewById<NestedScrollView>(R.id.tv5_scroll).getChildAt(0) as LinearLayout
        while(source.childCount>0){
            val child=source.getChildAt(0)
            source.removeViewAt(0)
            body.addView(child)
        }
        body.findViewWithTag<TextView>("amount").text=dong(q.getLong("total"))
        val method=book.value("method","COD")
        val names=mapOf("COD" to "Tiền mặt khi giao","MOMO" to "QR MoMo","VIETQR" to "QR ngân hàng")
        for(code in listOf("COD","MOMO","ZALO","VIETQR")){
            val title=body.findViewWithTag<TextView>(code)
            val row=title.parent.parent as LinearLayout
            val container=row.parent as View
            if(code=="ZALO"){container.visibility=View.GONE;continue}
            title.text=names[code]
            (row.getChildAt(row.childCount-1) as RadioButton).isChecked=method==code
            container.setOnClickListener{book.save("method",code);render()}
        }
        body.findViewWithTag<TextView>("selectedMethod").text=names[method]
        body.findViewWithTag<TextView>("methodHint").text=if(method=="COD") "Trả tiền khi nhận món." else "QR hiển thị sau khi tạo đơn, theo đúng mã đơn và tổng tiền."
        if(!live) label("Đơn demo lưu trên máy. Giá mẫu dùng VNĐ để thử QR.")
        button("Đặt đơn",footer){ createOrder() }
    }
    private fun createOrder(){
        val q=draft
        val key=book.draftId()
        book.orders().find { it.optString("request")==key }?.let { placed(it);return }
        val method=book.value("method","COD")
        val destination=q.getJSONObject("destination")
        if(live && (!destination.has("lat") || !destination.has("lng"))){message("Chọn địa chỉ có tọa độ GPS");return}
        val payload=JSONObject(q.toString()).put("payment_method",method)
            .put("customer_id",intent.getIntExtra("customer_id",0)).put("delivery_address",destination.getString("address"))
            .put("delivery_lat",destination.opt("lat")).put("delivery_lng",destination.opt("lng"))
        if(q.optString("voucher_code").isBlank())payload.put("voucher_code",JSONObject.NULL)
        fun persist(reply:JSONObject){
            val orderId=reply.getString("order_id")
            require(orderId.isNotBlank() && orderId!="null")
            q.put("schedule",if(live)"Giao ngay" else book.value("schedule","Giao ngay")).put("id",orderId).put("request",key).put("method",method)
                .put("payment",if(method=="COD") "Thu khi giao" else "Chờ thanh toán")
                .put("status",reply.optString("status","PENDING")).put("created",System.currentTimeMillis())
                .put("total",reply.getLong("total_amount")).put("token",reply.optString("qr_verify_token",""))
            listOf("momo_qr","bank_id","account_no","delivery_fee","discount").forEach { k->if(!reply.isNull(k))q.put(k,reply.get(k)) }
            book.put(q);placed(q)
        }
        if(live) runApi(api.create(payload.requestBody(),key),::persist)
        else persist(JSONObject().put("order_id","DEMO-"+key.take(8).uppercase()).put("total_amount",q.getLong("total"))
            .put("qr_verify_token","FF_QR_DEMO_"+UUID.randomUUID()))
    }
    private fun placed(o:JSONObject){
        book.save("draft","")
        if(o.getString("method")=="COD")go(19,o.getString("id"),true)
        else {intent.putExtra("order_id",o.getString("id"));render()}
    }
    private fun qr(payload:String, parent:LinearLayout) {
        val matrix=QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,600,600)
        val pixels=IntArray(600*600){ p->if(matrix[p%600,p/600]) Color.BLACK else Color.WHITE }
        val bitmap=Bitmap.createBitmap(pixels,600,600,Bitmap.Config.ARGB_8888)
        parent.addView(ImageView(a).apply{setImageBitmap(bitmap);contentDescription="Mã QR"},LinearLayout.LayoutParams(-1,dp(245)))
    }
    private fun showPayment(o:JSONObject){
        val method=o.getString("method")
        val box=card(method+" · "+dong(o.getLong("total")),"Nội dung: "+o.getString("id")+"\n"+o.optString("payment"))
        if(method=="VIETQR"){
            val bank=if(live)o.optString("bank_id") else book.value("bank")
            val account=if(live)o.optString("account_no") else book.value("account")
            if(bank.matches(Regex("[A-Za-z0-9]{2,15}")) && account.matches(Regex("[0-9]{4,24}"))){
                label("Người nhận: "+bank+" • "+account,box)
                val url=Uri.parse("https://img.vietqr.io/image/"+bank+"-"+account+"-compact2.png").buildUpon()
                    .appendQueryParameter("amount",o.getLong("total").toString()).appendQueryParameter("addInfo",o.getString("id")).build().toString()
                val image=ImageView(a)
                box.addView(image,LinearLayout.LayoutParams(-1,dp(270)))
                val progress=label("Đang tải VietQR…",box)
                jobs.execute {
                    val bitmap=runCatching{
                        val c=java.net.URL(url).openConnection().apply {connectTimeout=7000;readTimeout=10000}
                        c.getInputStream().use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                    a.runOnUiThread {if(image.isAttachedToWindow){
                        image.setImageBitmap(bitmap);progress.text=if(bitmap==null)"Tải QR thất bại. Kiểm tra mạng rồi thử lại." else "Quét bằng ứng dụng ngân hàng."
                    }}
                }
                button("Tải lại QR",box){render()}
            } else label("Chưa cấu hình ngân hàng nhận tiền.",box)
            if(!live) button("Cấu hình tài khoản nhận",box){
                prompt("Mã ngân hàng (VD: MB)",bank){ b->
                    prompt("Số tài khoản",account){ acc->
                        if(!b.matches(Regex("[A-Za-z0-9]{2,15}")) || !acc.matches(Regex("[0-9]{4,24}")))message("Mã ngân hàng hoặc tài khoản chưa hợp lệ")
                        else {book.save("bank",b);book.save("account",acc);render()}
                    }
                }
            }
        } else if(method=="MOMO"){
            val payload=o.optString("momo_qr")
            if(payload.isNotBlank())qr(payload,box)
            else if(!live){
                val uri=book.value("momo_image")
                if(uri.isNotBlank())box.addView(ImageView(a).apply{setImageURI(Uri.parse(uri))},LinearLayout.LayoutParams(-1,dp(245)))
                label("Dùng QR nhận tiền từ ví MoMo của anh. Với QR cá nhân, nhập số tiền và mã đơn hiển thị ở trên.",box)
                button("Chọn ảnh QR MoMo",box){selectQr.launch(arrayOf("image/*"))}
            } else label("Máy chủ chưa trả QR MoMo cho đơn này.",box)
        }
        if(o.optString("payment")=="Chờ thanh toán")button("Tôi đã chuyển · chờ đối soát"){
            o.put("payment","Khách báo đã chuyển • chờ đối soát");book.put(o);render()
        }
        button("Xem đơn hàng",footer){go(19,o.getString("id"),true)}
    }
    private fun refresh(o:JSONObject){
        runApi(api.details(o.getString("id"))){r->
            listOf("status","courier_lat","courier_lng","momo_qr","payment_status","qr_verify_token").forEach{k->
                if(!r.isNull(k))o.put(when(k){"payment_status"->"payment";"qr_verify_token"->"token";else->k},r.get(k))
            }
            book.put(o);render()
        }
    }
    private fun tracking(o:JSONObject){
        card("Đơn "+id,status(o))
        val destination=o.getJSONObject("destination")
        val points=mutableListOf<Pair<String,GeoPoint>>()
        fun point(name:String,j:JSONObject,lat:String,lng:String){
            val y=j.optDouble(lat);val x=j.optDouble(lng)
            if(y in -90.0..90.0 && x in -180.0..180.0)points.add(name to GeoPoint(y,x))
        }
        point("Quán",o,"restaurant_lat","restaurant_lng");point("Giao đến",destination,"lat","lng")
        point("Shipper",o,"courier_lat","courier_lng")
        Configuration.getInstance().userAgentValue=a.packageName
        Configuration.getInstance().osmdroidBasePath=a.cacheDir.resolve("osm")
        Configuration.getInstance().osmdroidTileCache=a.cacheDir.resolve("osm/tiles")
        val view=MapView(a);map=view;view.setTileSource(TileSourceFactory.MAPNIK);view.setMultiTouchControls(true)
        body.addView(view,LinearLayout.LayoutParams(-1,dp(235)))
        for((name,p) in points)view.overlays.add(Marker(view).apply {position=p;title=name;setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)})
        view.controller.setZoom(15.0);view.controller.setCenter(points.lastOrNull()?.second ?: GeoPoint(10.7756,106.6924))
        if(points.size>1)view.post {if(map===view)view.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(points.map{it.second}),false,dp(44),17.0,0L)}
        view.onResume()
        label("© OpenStreetMap contributors • "+destination.optString("address"))
        if(!o.has("courier_lat"))label("Chưa có vị trí shipper.")
        if(live)button("Cập nhật từ máy chủ"){refresh(o)}
        else if(o.optString("status") !in listOf("CANCELLED","COMPLETED"))button("Mô phỏng bước giao tiếp theo"){
            val steps=listOf("PENDING","CONFIRMED","PREPARING","DELIVERING","COMPLETED")
            o.put("status",steps[(steps.indexOf(o.optString("status"))+1).coerceIn(0,4)]);book.put(o);render()
        }
        button("Nhắn cho shipper",footer){go(21)};button("Chi tiết đơn"){go(22)}
    }
    private fun details(o:JSONObject){
        card(id,status(o)+" • "+o.getString("payment"));summary(o)
        card("Giao đến",o.getJSONObject("destination").getString("address")+" • "+o.optString("schedule","Giao ngay"))
        val token=o.optString("token")
        if(token.isNotBlank() && o.optString("status") !in listOf("CANCELLED","COMPLETED"))
            qr(token,card("QR nhận hàng","Cho shipper quét khi nhận món."))
        button("Theo dõi đơn"){go(20)}
        if(live)button("Cập nhật trạng thái"){refresh(o)}
        if(o.optString("method")!="COD" && o.optString("status")!="CANCELLED")button("QR thanh toán"){go(18)}
        if(o.optString("status")=="PENDING")button("Hủy đơn"){
            AlertDialog.Builder(a).setMessage("Hủy đơn "+id+"?").setNegativeButton("Giữ đơn",null).setPositiveButton("Hủy đơn"){_,_->
                fun cancelled(){o.put("status","CANCELLED");book.put(o);render()}
                if(live) runApi(api.cancel(id,JSONObject().put("reason","Khách yêu cầu hủy").requestBody())){cancelled()} else cancelled()
            }.show()
        }
        if(o.optString("status")=="COMPLETED")button("Đánh giá"){go(23)}
        button("Biên nhận",footer){go(25)};button("Danh sách đơn"){go(24)}
    }

    private fun chat(o: JSONObject) {
        val orderId = o.getString("id")
        val draftKey = "draftChat$orderId"
        val roleKey = "chatDemoRole$orderId"
        val demoRole = book.value(roleKey, "CUSTOMER")
        val blue = Color.rgb(0, 100, 220)
        val gray = Color.rgb(235, 237, 240)

        fun rounded(color: Int, radius: Int) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
            }

        val toolbar = a.findViewById<LinearLayout>(R.id.tv5_toolbar)
        val heading = toolbar.getChildAt(1) as TextView
        heading.text = if (!live && demoRole == "SHIPPER") "Khách hàng" else "Shipper"

        val statusLine = TextView(a).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(10))
        }
        body.addView(statusLine)

        fun status(text: String, online: Boolean = false) {
            statusLine.text = text
            statusLine.setTextColor(
                if (online) Color.rgb(17, 122, 68) else Color.DKGRAY
            )
        }

        val thread = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(thread)

        fun bubble(text: String, mine: Boolean, time: Long) {
            val side = if (mine) Gravity.END else Gravity.START
            val group = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = side
            }
            thread.addView(group, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(5)
                bottomMargin = dp(3)
                if (mine) marginStart = dp(40) else marginEnd = dp(40)
            })

            group.addView(TextView(a).apply {
                this.text = text
                textSize = 16f
                setTextColor(if (mine) Color.WHITE else Color.rgb(30, 31, 34))
                background = rounded(if (mine) blue else gray, 18)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setTextIsSelectable(true)
            }, LinearLayout.LayoutParams(-2, -2))

            if (time > 0L) {
                group.addView(TextView(a).apply {
                    this.text = android.text.format.DateFormat
                        .getTimeFormat(a).format(java.util.Date(time))
                    textSize = 11f
                    setTextColor(Color.DKGRAY)
                    setPadding(dp(4), dp(2), dp(4), 0)
                })
            }
        }

        fun scrollToLatest() {
            scroll.post { scroll.smoothScrollTo(0, body.height) }
        }

        if (!live) {
            val roleName = if (demoRole == "CUSTOMER") "Khách" else "Shipper"
            status("● Online mô phỏng · Đơn $orderId", true)

            button("Đang đóng vai $roleName · Đổi vai", footer) {
                val nextRole = if (demoRole == "CUSTOMER") "SHIPPER" else "CUSTOMER"
                book.save(roleKey, nextRole)
                render()
            }
        } else {
            status("Đang kết nối · Đơn $orderId")
        }

        val composer = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(6))
        }
        footer.addView(composer, LinearLayout.LayoutParams(-1, -2))

        val currentDraftKey = if (!live && demoRole == "SHIPPER") {
            "${draftKey}_shipper"
        } else {
            draftKey
        }

        val input = EditText(a).apply {
            id = R.id.tv5_message_input
            hint = "Nhập tin nhắn…"
            textSize = 15f
            maxLines = 4
            background = rounded(gray, 22)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setText(book.value(currentDraftKey))
            setSelection(length())
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val send = ImageButton(a).apply {
            id = R.id.tv5_send
            contentDescription = "Gửi tin nhắn"
            setImageResource(R.drawable.tv5_ic_send)
            setColorFilter(Color.WHITE)
            background = rounded(blue, 24)
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginStart = dp(8)
        })

        var sending = false

        fun updateSendButton() {
            send.isEnabled = !sending && input.text.isNotBlank()
            send.alpha = if (send.isEnabled) 1f else 0.45f
        }

        input.doAfterTextChanged {
            book.save(currentDraftKey, it.toString())
            updateSendButton()
        }
        updateSendButton()

        if (!live) {
            val messages = o.optJSONArray("chat") ?: JSONArray()

            for (i in 0 until messages.length()) {
                val item = messages.optJSONObject(i)
                if (item == null) {
                    // Tin cũ của bản trước được lưu dưới dạng chuỗi.
                    bubble(messages.optString(i), demoRole == "CUSTOMER", 0L)
                } else {
                    bubble(
                        item.optString("text"),
                        item.optString("role") == demoRole,
                        item.optLong("time")
                    )
                }
            }

            send.setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener

                val time = System.currentTimeMillis()
                messages.put(
                    JSONObject()
                        .put("role", demoRole)
                        .put("text", text)
                        .put("time", time)
                )
                o.put("chat", messages)
                book.put(o)

                bubble(text, true, time)
                input.text.clear()
                scrollToLatest()
            }

            scrollToLatest()
            return
        }

        fun screenOpen() =
            active && input.isAttachedToWindow && !a.isDestroyed && !a.isFinishing

        fun request(
            call: Call<ResponseBody>,
            done: (JSONObject?, String?) -> Unit
        ) {
            call.enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: retrofit2.Response<ResponseBody>
                ) {
                    if (!response.isSuccessful) {
                        response.errorBody()?.close()
                        if (screenOpen()) done(null, "HTTP ${response.code()}")
                        return
                    }

                    val result = runCatching {
                        val raw = response.body()?.use { it.string() }.orEmpty()
                        val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                        json.optJSONObject("data") ?: json
                    }
                    if (screenOpen()) {
                        done(result.getOrNull(), result.exceptionOrNull()?.message)
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, error: Throwable) {
                    if (screenOpen()) done(null, error.localizedMessage ?: "Lỗi kết nối")
                }
            })
        }

        var previousMessages: String? = null

        val refresh = object : Runnable {
            override fun run() {
                if (!screenOpen()) return

                request(api.getChat(orderId)) { data, error ->
                    val messages = data?.optJSONArray("messages")

                    if (data == null || messages == null) {
                        status("Chưa tải được hội thoại · ${error ?: "Thiếu dữ liệu"}")
                    } else {
                        heading.text = data.optString("peer_name", "Người giao hàng")
                        when (data.opt("peer_online")) {
                            true -> status("● Đang hoạt động · Đơn $orderId", true)
                            false -> status("○ Ngoại tuyến · Đơn $orderId")
                            else -> status("Chưa rõ trạng thái · Đơn $orderId")
                        }

                        val snapshot = messages.toString()
                        if (snapshot != previousMessages) {
                            val nearBottom = previousMessages == null ||
                                    body.height - scroll.height - scroll.scrollY < dp(80)

                            thread.removeAllViews()
                            for (i in 0 until messages.length()) {
                                val item = messages.optJSONObject(i) ?: continue
                                bubble(
                                    item.optString("text"),
                                    item.optBoolean("mine"),
                                    item.optLong("time")
                                )
                            }
                            previousMessages = snapshot
                            if (nearBottom) scrollToLatest()
                        }
                    }
                    handler.postDelayed(this, 2500L)
                }
            }
        }

        input.post { refresh.run() }

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty() || sending) return@setOnClickListener

            sending = true
            input.isEnabled = false
            updateSendButton()

            val payload = JSONObject().put("text", text).requestBody()
            request(api.sendChat(orderId, payload)) { data, error ->
                sending = false
                input.isEnabled = true

                if (data != null) {
                    input.text.clear()
                    scrollToLatest()
                } else {
                    message("Chưa xác nhận được tin đã gửi. ${error.orEmpty()}")
                }
                updateSendButton()
            }
        }
    }
    private fun review(o:JSONObject){
        if(o.optString("status")!="COMPLETED"){card("Đánh giá mở sau khi giao hàng");button("Theo dõi đơn"){go(20)};return}
        val box=card("Đánh giá đơn "+id)
        val stars=RatingBar(a).apply{numStars=5;stepSize=1f;rating=o.optInt("rating",0).toFloat()}
        box.addView(stars,LinearLayout.LayoutParams(-2,-2))
        val input=field("Viết nhận xét",o.optString("review"),box)
        input.id=R.id.tv5_review_text
        input.filters=arrayOf(android.text.InputFilter.LengthFilter(500))
        val count=label(input.length().toString()+" / 500",box)
        count.id=R.id.tv5_review_counter
        input.doAfterTextChanged {count.text=input.length().toString()+" / 500"}
        button("Lưu đánh giá",footer){
            if(stars.rating<1)message("Chọn số sao trước khi lưu") else {
                o.put("rating",stars.rating.toInt()).put("review",input.text.toString().trim());book.put(o);message("Đã lưu đánh giá trên máy");go(22,finish=true)
            }
        }
    }
    private fun orders(){
        val filter=book.value("orderFilter","Tất cả")
        button("Lọc đơn • "+filter){
            val names=arrayOf("Tất cả","Đang xử lý","Đã giao","Đã hủy")
            AlertDialog.Builder(a).setItems(names){_,i->book.save("orderFilter",names[i]);render()}.show()
        }
        val list=book.orders().filter{when(filter){
            "Đã giao"->it.optString("status")=="COMPLETED"
            "Đã hủy"->it.optString("status")=="CANCELLED"
            "Đang xử lý"->it.optString("status") !in listOf("COMPLETED","CANCELLED")
            else->true
        }}
        if(list.isEmpty())card("Chưa có đơn trong mục này")
        list.forEach { o->
            val box=card(o.getString("id"),status(o)+" • "+dong(o.getLong("total")))
            button("Chi tiết đơn",box){go(22,o.getString("id"))}
        };nav()
    }
    private fun profile(){
        card(book.value("profile","Khoa"),if(live)"Tài khoản khách" else "Hồ sơ demo")
        button("Sửa tên"){prompt("Tên hiển thị",book.value("profile","Khoa")){book.save("profile",it);render()}}
        listOf("Đơn hàng" to 24,"Ví điểm" to 27,"Địa chỉ đã lưu" to 28,"Thông báo" to 29,"Trợ giúp & cài đặt" to 30).forEach{(s,n)->button(s){go(n)}}
        nav()
    }
    private fun loyalty(){
        val complete=book.orders().filter{it.optString("status")=="COMPLETED"}
        if(live)card("Điểm thành viên","Chờ dữ liệu điểm từ hệ thống thành viên.")
        else{
            val earned=complete.sumOf{it.getLong("total")/1000}
            card("Điểm thưởng demo",earned.toString()+" điểm • 1 điểm cho mỗi 1.000 đ của đơn đã giao")
            complete.forEach{card(it.getString("id"),"+"+(it.getLong("total")/1000)+" điểm")}
        };nav()
    }
    private fun addresses(){
        val selected=book.prefs.getInt("addressIndex",0)
        book.addresses().objects().forEachIndexed { i,entry->
            val box=card(entry.optString("name"),entry.getString("address")+"\n"+entry.optString("source"))
            button(if(i==selected)"✓ Đang chọn" else "Chọn địa chỉ",box){
                book.prefs.edit().putInt("addressIndex",i).apply();render()
            }
        }
        button("Dùng vị trí hiện tại"){if(hasPermission())locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))}
        button("Thêm địa chỉ"){prompt("Địa chỉ giao hàng"){s->
            book.addAddress(JSONObject().put("name","Địa chỉ đã lưu").put("address",s).put("source","Nhập tay"));render()
        }}
        button("Xác nhận địa chỉ",footer){a.finish()}
    }
    private fun hasPermission()=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any{
        a.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED
    }
    private val timeout=Runnable { stopLocation();if(active)fallback("Chưa nhận được tín hiệu GPS") }
    private fun stopLocation(){
        handler.removeCallbacks(timeout);listener?.let{runCatching{locations.removeUpdates(it)}};listener=null
    }
    private fun fallback(reason:String){
        AlertDialog.Builder(a).setTitle(reason).setMessage("Dùng điểm mẫu Quận 1 (10.7756, 106.6924) để thử luồng giao hàng?")
            .setNegativeButton("Giữ địa chỉ",null).setPositiveButton("Dùng điểm mẫu"){_,_->savePoint(10.7756,106.6924,"Điểm mẫu Quận 1")}.show()
    }
    private fun savePoint(lat:Double,lng:Double,source:String){
        val coords=String.format(java.util.Locale.US,"%.5f, %.5f",lat,lng)
        prompt("Địa chỉ giao tại "+coords,coords){s->
            book.addAddress(JSONObject().put("name",source).put("address",s).put("lat",lat).put("lng",lng).put("source",source));render()
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun locate(){
        stopLocation()
        if(!hasPermission())return
        val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter{runCatching{locations.isProviderEnabled(it)}.getOrDefault(false)}
        if(providers.isEmpty()){fallback("Vị trí đang tắt");return}
        val callback=object:LocationListener{
            override fun onLocationChanged(l:Location){
                if(listener!==this)return
                stopLocation()
                if(kotlin.math.abs(l.latitude-37.42)<0.04 && kotlin.math.abs(l.longitude+122.08)<0.04 && (Build.MODEL.contains("sdk",true)||Build.FINGERPRINT.contains("generic",true)))
                    fallback("Máy ảo đang ở tọa độ mặc định Mỹ")
                else savePoint(l.latitude,l.longitude,"GPS thiết bị")
            }
            override fun onProviderEnabled(p:String){}
            override fun onProviderDisabled(p:String){}
            @Deprecated("API cũ") override fun onStatusChanged(p:String,s:Int,b:Bundle?){}
        }
        listener=callback
        val started=providers.map{runCatching{locations.requestLocationUpdates(it,0L,0f,callback,Looper.getMainLooper())}.isSuccess}.any{it}
        if(!started){stopLocation();fallback("Không lấy được vị trí");return}
        message("Đang tìm vị trí…");handler.postDelayed(timeout,15000)
    }
    private fun notifications(){
        if(!book.prefs.getBoolean("notify",true)){card("Thông báo đang tắt");button("Mở cài đặt"){go(30)};nav();return}
        val seen=book.value("read")
        book.orders().forEach{ o->
            val event=o.getString("id")+"="+o.optString("status")
            val box=card(o.getString("id"),status(o)+(if(event in seen.split('|'))" • Đã đọc" else " • Mới"))
            button("Xem đơn",box){book.save("read",book.value("read")+"|"+event);go(22,o.getString("id"))}
        }
        if(book.orders().isEmpty())card("Chưa có thông báo đơn hàng")
        button("Đánh dấu tất cả đã đọc"){book.save("read",book.orders().joinToString("|"){it.getString("id")+"="+it.optString("status")});render()};nav()
    }
    private fun settings(){
        card("Trợ giúp")
        button("Làm sao để hủy đơn?"){AlertDialog.Builder(a).setMessage("Mở Đơn hàng → Chi tiết đơn → Hủy đơn khi quán chưa xác nhận.").setPositiveButton("Đóng",null).show()}
        button("Đổi địa chỉ giao hàng"){go(28)}
        button("Kiểm tra thanh toán"){go(24)}
        val preferred=Switch(a).apply{text="Thông báo đơn trong ứng dụng";isChecked=book.prefs.getBoolean("notify",true)}
        body.addView(preferred)
        preferred.setOnCheckedChangeListener{_,on->book.prefs.edit().putBoolean("notify",on).apply()}
        nav()
    }
}

