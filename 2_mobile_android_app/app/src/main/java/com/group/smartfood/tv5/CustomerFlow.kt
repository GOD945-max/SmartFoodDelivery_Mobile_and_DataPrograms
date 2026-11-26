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
    private val book = OrderBook(a.getSharedPreferences(if (live) "tv5_user_" + intent.getIntExtra("customer_id",0) else "tv5_demo",0), live)
    private val api by lazy { RetrofitClient.create(intent.getStringExtra("api_base_url").orEmpty(), intent.getStringExtra("jwt").orEmpty()) }
    private val cart get() = intent.getStringExtra("cart_json")?.let(::JSONObject) ?: book.sampleCart()
    private val draft get() = book.quote(cart, when(val saved=book.value("voucher", if(live) "" else "FOODFLOW20")) {
        "SMART20" -> if(live) "" else "FOODFLOW20"
        "FREESHIP" -> if(live) "" else "FREESHIP15K"
        else -> saved
    })
    private val id get() = intent.getStringExtra("order_id")?.takeIf { it.isNotBlank() } ?: book.orders().firstOrNull()?.optString("id").orEmpty()
    private val order get() = book.order(id)
    private val scroll = a.findViewById<NestedScrollView>(R.id.tv5_scroll)
    private val body = a.findViewById<LinearLayout>(R.id.tv5_dynamic_content) ?: (scroll.getChildAt(0) as LinearLayout)
    private val footer = a.findViewById<LinearLayout>(R.id.tv5_footer)
    private val jobs = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var gpsSocket: com.group.smartfood.utils.SocketManager? = null
    private var map: MapView? = null
    private var listener: LocationListener? = null
    private val locations = a.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var busy = false
    private var active = false
    private val permission = a.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(it.values.any { allowed -> allowed }) locate() else fallback("Chưa cấp quyền vị trí")
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
        val box=a.layoutInflater.inflate(R.layout.tv5_order_card,body,false) as LinearLayout
        body.addView(box)
        box.findViewById<TextView>(R.id.tv5_card_title).text=title
        box.findViewById<TextView>(R.id.tv5_card_detail).apply {
            text=detail
            visibility=if(detail.isEmpty()) View.GONE else View.VISIBLE
        }
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
        gpsSocket?.close(); gpsSocket=null
        active=true
        map?.onDetach(); map=null
        body.removeAllViews();footer.removeAllViews();footer.orientation=LinearLayout.VERTICAL
        try {
            when(screen) {
                16->checkout()
                17->vouchers()
                18->payment()
                21 -> if (live) { card("Chat hiện chỉ dùng trong bản demo"); nav() }
                      else if (order != null) chat(id, order)
                      else { card("Chưa có đơn hàng"); nav() }
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
                        22->details(o)
                        23->review(o)
                        25->{card("Biên nhận",receiptText(o));button("Lưu biên nhận",footer){receipt.launch("SmartFood-"+id+".txt")}}
                    }
                }
            }
        } catch(e:Exception) { card("Không đọc được dữ liệu", e.localizedMessage ?: "Kiểm tra lại giỏ hàng.") }
    }
    fun pause(){ active=false; gpsSocket?.close(); gpsSocket=null; stopLocation();map?.onPause() }
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
        "ACCEPTED"->"Quán đã xác nhận"
        "COOKING"->"Đang chuẩn bị món"
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
        slot("address").text=book.address().optString("address", "Chọn địa chỉ giao hàng")
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
        fun choose(code: String, saving: Long) {
            button(code.ifEmpty { "Bỏ voucher" }) {
                book.save("voucher",code)
                book.save("voucherSaving",saving.toString())
                book.save("voucherSubtotal",draft.getLong("subtotal").toString())
                a.finish()
            }
        }
        choose("",0)
        if (live) {
            val q=draft
            runApi(api.vouchers(q.getInt("restaurant_id"),q.getLong("subtotal"),q.getLong("delivery_fee"))) { reply ->
                if(active) reply.getJSONArray("data").objects().filter { it.optBoolean("is_eligible") }
                    .forEach { choose(it.getString("code"),it.optLong("estimated_saving")) }
            }
        } else OrderBook.demoCodes.forEach { choose(it,0) }
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
                val json=when(val parsed=org.json.JSONTokener(raw).nextValue()) {
                    is JSONArray -> JSONObject().put("data",parsed)
                    is JSONObject -> parsed
                    else -> error("Phản hồi API không hợp lệ")
                }
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
        val method=book.value("method","COD").takeIf { it == "VIETQR" } ?: "COD"
        val names=mapOf("COD" to "Tiền mặt khi giao","VIETQR" to "QR ngân hàng")
        for(code in listOf("COD","VIETQR")){
            val title=body.findViewWithTag<TextView>(code)
            val row=title.parent.parent as LinearLayout
            val container=row.parent as View
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
        val method=book.value("method","COD").takeIf { it == "VIETQR" } ?: "COD"
        val destination=q.getJSONObject("destination")
        if (live && (!destination.optBoolean("confirmedGps") ||
                    destination.optString("address").isBlank() ||
                    destination.optDouble("lat") !in -90.0..90.0 ||
                    destination.optDouble("lng") !in -180.0..180.0)) {
            message("Anh cần chọn vị trí GPS và xác nhận địa chỉ giao hàng.")
            go(28)
            return
        }
        val payload=JSONObject(q.toString()).put("payment_method",method)
            .put("customer_id",intent.getIntExtra("customer_id",0)).put("delivery_address",destination.getString("address"))
            .put("delivery_lat",destination.opt("lat")).put("delivery_lng",destination.opt("lng"))
        if(q.optString("voucher_code").isBlank())payload.put("voucher_code",JSONObject.NULL)
        fun persist(reply:JSONObject){
            val orderId=reply.get("id").toString()
            require(orderId.isNotBlank() && orderId!="null")
            q.put("schedule",if(live)"Giao ngay" else book.value("schedule","Giao ngay")).put("id",orderId).put("request",key).put("method",method)
                .put("payment",if(method=="COD") "Thu khi giao" else "Chờ thanh toán")
                .put("status",reply.optString("order_status","PENDING")).put("created",System.currentTimeMillis())
                .put("total",reply.getLong("total_amount")).put("token",reply.optString("qr_verify_token",""))
            if (live) applyServerOrder(reply,q)
            book.put(q);placed(q)
        }
        if(live) runApi(api.create(payload.requestBody(),key),::persist)
        else persist(JSONObject().put("id","DEMO-"+key.take(8).uppercase()).put("total_amount",q.getLong("total"))
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
            val url=com.group.smartfood.utils.VietQrHelper.buildVietQrUrl(o.getLong("total"),o.getString("id"))
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
        }
        if(o.optString("payment")=="Chờ thanh toán")button("Tôi đã chuyển · chờ đối soát"){
            o.put("payment","Khách báo đã chuyển • chờ đối soát");book.put(o);render()
        }
        button("Xem đơn hàng",footer){go(19,o.getString("id"),true)}
    }
    private fun refresh(o:JSONObject){
        runApi(api.details(o.getString("id"))){r->
            applyServerOrder(r, o)
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
        val gpsHint = if (!o.has("courier_lat")) label("Chưa có vị trí shipper.") else null
        if (live) {
            var marker = view.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "Shipper" }
            gpsSocket = com.group.smartfood.utils.SocketManager(intent.getStringExtra("api_base_url").orEmpty(),
                intent.getStringExtra("jwt").orEmpty()).also { socket ->
                socket.track(o.getString("id")) { lat,lng -> a.runOnUiThread {
                    if (active && map === view) {
                        val pin = marker ?: Marker(view).also { it.title="Shipper"; view.overlays.add(it); marker=it }
                        pin.position=GeoPoint(lat,lng)
                        gpsHint?.visibility=View.GONE
                        o.put("courier_lat",lat).put("courier_lng",lng)
                        view.invalidate()
                    }
                } }
            }
        }
        if(live)button("Cập nhật từ máy chủ"){refresh(o)}
        else if(o.optString("status") !in listOf("CANCELLED","COMPLETED"))button("Mô phỏng bước giao tiếp theo"){
            val steps=listOf("PENDING","ACCEPTED","COOKING","DELIVERING","COMPLETED")
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
        if(o.optString("method")=="VIETQR" && o.optString("status")!="CANCELLED")button("QR thanh toán"){go(18)}
        if(o.optString("status") in setOf("PENDING","ACCEPTED"))button("Hủy đơn"){
            AlertDialog.Builder(a).setMessage("Hủy đơn "+id+"?").setNegativeButton("Giữ đơn",null).setPositiveButton("Hủy đơn"){_,_->
                fun cancelled(){o.put("status","CANCELLED");book.put(o);render()}
                if(live) runApi(api.cancel(id,JSONObject().put("reason","Khách yêu cầu hủy").requestBody())){cancelled()} else cancelled()
            }.show()
        }
        if(o.optString("status")=="COMPLETED")button("Đánh giá"){go(23)}
        button("Biên nhận",footer){go(25)};button("Danh sách đơn"){go(24)}
    }

    private fun chat(orderId: String, localOrder: JSONObject?) {
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

        fun bubble(text: String, mine: Boolean, time: Long, index: Int = thread.childCount) {
            val side = if (mine) Gravity.END else Gravity.START
            val group = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = side
            }
            thread.addView(group, index, LinearLayout.LayoutParams(-1, -2).apply {
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

        val composer=a.layoutInflater.inflate(R.layout.tv5_chat_composer,footer,false)
        footer.addView(composer)
        val currentDraftKey=if(demoRole=="SHIPPER") "${draftKey}_shipper" else draftKey
        val input=composer.findViewById<EditText>(R.id.tv5_message_input)
        input.setText(book.value(currentDraftKey))
        input.setSelection(input.length())
        val send=composer.findViewById<ImageButton>(R.id.tv5_send)

        fun updateSendButton() {
            send.isEnabled = input.text.isNotBlank()
            send.alpha = if (send.isEnabled) 1f else 0.45f
        }

        input.doAfterTextChanged {
            book.save(currentDraftKey, it.toString())
            updateSendButton()
        }
        updateSendButton()

        if (!live) {
            val o = requireNotNull(localOrder)
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

    }
    private fun review(o:JSONObject){
        if(o.optString("status")!="COMPLETED"){card("Đánh giá mở sau khi giao hàng");button("Theo dõi đơn"){go(20)};return}
        val box=card("Đánh giá đơn "+id)
        val stars=RatingBar(a).apply{numStars=5;stepSize=1f;rating=o.optInt("rating",0).toFloat()}
        box.addView(stars,LinearLayout.LayoutParams(-2,-2))
        val editor=a.layoutInflater.inflate(R.layout.tv5_review_editor,box,false)
        box.addView(editor)
        val input=editor.findViewById<EditText>(R.id.tv5_review_text)
        input.setText(o.optString("review"))
        val count=editor.findViewById<TextView>(R.id.tv5_review_counter)
        count.text=input.length().toString()+" / 500"
        input.doAfterTextChanged {count.text=input.length().toString()+" / 500"}
        button("Lưu đánh giá",footer){
            if(stars.rating<1)message("Chọn số sao trước khi lưu") else {
                o.put("rating",stars.rating.toInt()).put("review",input.text.toString().trim());book.put(o);message("Đã lưu đánh giá trên máy");go(22,finish=true)
            }
        }
    }
    private fun orders(cached: Boolean = false, offset: Int = 0, more: Boolean = false){
        val filter=book.value("orderFilter","Tất cả")
        button("Lọc đơn • "+filter){
            val names=arrayOf("Tất cả","Đang xử lý","Đã giao","Đã hủy")
            AlertDialog.Builder(a).setItems(names){_,i->book.save("orderFilter",names[i]);render()}.show()
        }
        if (live && !cached) {
            val anchor = label("Đang đồng bộ đơn hàng…")
            runApi(api.customerOrders(intent.getIntExtra("customer_id",0),20,offset)) { reply ->
                if (active && anchor.isAttachedToWindow) {
                    val rows = reply.getJSONArray("data").objects()
                    val all = book.orders().associateBy { it.getString("id") }.toMutableMap()
                    for (row in rows) {
                        val key = row.get("id").toString()
                        all[key] = applyServerOrder(row, JSONObject(all[key]?.toString() ?: "{}"))
                    }
                    book.save("orders", JSONArray(all.values.sortedByDescending { it.optLong("id") }).toString())
                    body.removeAllViews(); footer.removeAllViews()
                    orders(true, offset + rows.size, rows.size == 20)
                }
            }
            anchor.text = "Đơn đã lưu trên máy · đang thử cập nhật từ máy chủ"
        }
        if (more) button("Tải thêm đơn") { body.removeAllViews(); footer.removeAllViews(); orders(false,offset) }
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
        if (live) { message("$reason. Hãy thử lấy lại vị trí GPS."); return }
        AlertDialog.Builder(a).setTitle(reason).setMessage("Dùng điểm mẫu Quận 1 (10.7756, 106.6924) để thử luồng giao hàng?")
            .setNegativeButton("Giữ địa chỉ",null).setPositiveButton("Dùng điểm mẫu"){_,_->savePoint(10.7756,106.6924,"Điểm mẫu Quận 1")}.show()
    }
    private fun savePoint(lat:Double,lng:Double,source:String){
        val coords=String.format(java.util.Locale.US,"%.5f, %.5f",lat,lng)
        prompt("Địa chỉ giao tại "+coords,coords){s->
            book.addAddress(JSONObject().put("name",source).put("address",s).put("lat",lat).put("lng",lng).put("confirmedGps",live).put("source",source));render()
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

