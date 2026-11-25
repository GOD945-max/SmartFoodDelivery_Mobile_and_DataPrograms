package com.group.smartfood

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.group.smartfood.databinding.ActivityMainBinding
import com.group.smartfood.utils.AppConfig
import com.group.smartfood.utils.LocationHelper
import com.group.smartfood.utils.VietQrHelper

/**
 * Màn hình khởi tạo khung dự án Android chuẩn (TV1).
 * Khi TV4 & TV5 ghép LoginActivity vào ui/auth/, chỉ cần đổi LAUNCHER trong AndroidManifest.xml.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootScroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        binding.tvEnvInfo.text = buildString {
            append("Package: ${BuildConfig.APPLICATION_ID}\n")
            append("Version: ${BuildConfig.VERSION_NAME} • API Base: ${AppConfig.BASE_URL}")
        }

        binding.btnCheckLocation.setOnClickListener {
            val safeLoc = LocationHelper.resolveSafeVietnamLocation(37.421998, -122.084000)
            binding.tvDiagnosticResult.text =
                "Emulator US (37.42, -122.08) -> Fallback Q1 TP.HCM: (${safeLoc.first}, ${safeLoc.second})"
        }

        binding.btnTestVietQr.setOnClickListener {
            val url = VietQrHelper.buildVietQrUrl(133000, "FF_ORDER_DEMO1")
            binding.tvDiagnosticResult.text = "VietQR URL sẵn sàng:\n$url"
        }
    }
}
