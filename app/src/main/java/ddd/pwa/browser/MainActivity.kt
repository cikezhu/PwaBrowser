package ddd.pwa.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest


class MainActivity : AppCompatActivity() {
    private lateinit var shortcutLogo: Bitmap
    private lateinit var shortcutIcon: ImageView
    private val mTAG: String = "MainActivity"
    private val mScope = MainScope()
    private val webViewLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            Log.e(mTAG, data.toString())
            val url = data!!.getStringExtra("url")
            val name = data.getStringExtra("name")
            val full = data.getBooleanExtra("full", true)
            @Suppress("DEPRECATION") val logo = data.getParcelableExtra<Bitmap>("logo")
            if (url !== null && name !== null && logo !== null) {
                showShortcutCreationDialog(name, url, logo, full)
            }
        }
    }
    private val iconLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val selectedImageUri: Uri? = data.data
                selectedImageUri?.let {
                    val inputStream = this.contentResolver.openInputStream(it)
                    shortcutLogo = BitmapFactory.decodeStream(inputStream)
                    shortcutIcon.setImageBitmap(shortcutLogo)
                    inputStream?.close()
                }
            }
        }
    }
    
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 绑定组件
        val myInputUrl: EditText = findViewById(R.id.input_url)
        val myVersion: TextView = findViewById(R.id.version)
        @Suppress("DEPRECATION") val packageInfo = packageManager.getPackageInfo(packageName, 0)
        myVersion.text = "v${packageInfo.versionName}  by: 叮叮当"
        val myChangeUrl: Button = findViewById(R.id.button_change_url)
        myChangeUrl.setOnClickListener {
            Log.d(mTAG, "initialize: " + myChangeUrl.text.toString())
            // 隐藏输入法
            val inputMethodManager: InputMethodManager =
                applicationContext.getSystemService(
                    INPUT_METHOD_SERVICE
                ) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(myInputUrl.windowToken, 0)
            // 前往webView获取参数
            val hostUrl = myInputUrl.text.toString()
            if (hostUrl != "") {
                Toast.makeText(applicationContext,"正在连接..",Toast.LENGTH_SHORT).show()
                val intent = Intent(this@MainActivity, WebViewActivity::class.java)
                intent.putExtra("mode", LAUNCH_MODE.GET_URL_DETAIL.intValue)
                intent.putExtra("url", hostUrl)
                webViewLauncher.launch(intent)
            } else {
                Toast.makeText(applicationContext,"请输入网址> http(s)://....",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addShortcut(name: String, url: String, logo: Bitmap, full: Boolean) {
        // 添加快捷方式到桌面
        mScope.launch(Dispatchers.Main) {
            // 创建快捷方式
            val shortcutInfo = ShortcutInfo.Builder(applicationContext, LAUNCH_MODE.SHOW_URL_PAGE.intValue.toString() + name + url + generateUniqueValueFromBitmap(logo))
                .setShortLabel(name)
                .setIcon(Icon.createWithBitmap(logo))
                .setIntent(Intent(applicationContext, LauncherShortcutActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    putExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue)
                    putExtra("url", url)
                    putExtra("name", name)
                    putExtra("full", full)
                })
                .build()
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            shortcutManager.requestPinShortcut(shortcutInfo, null)
        }
    }

    private fun generateUniqueValueFromBitmap(logo: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        logo.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(byteArray)
        val digest = messageDigest.digest()

        // 将字节数组转换为十六进制字符串
        val hexString = StringBuilder()
        for (byte in digest) {
            val hex = Integer.toHexString(0xFF and byte.toInt())
            if (hex.length == 1) {
                hexString.append('0')
            }
            hexString.append(hex)
        }

        return hexString.toString()
    }
    private fun showShortcutCreationDialog(name: String, url: String, logo: Bitmap, full: Boolean) {
        shortcutLogo = logo
        val alertDialogBuilder = AlertDialog.Builder(this)
        // 创建布局
        val layoutInflater = LayoutInflater.from(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_shortcut_creation, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.shortcut_name)
        nameInput.setText(name)
        // 选择图标的按钮
        shortcutIcon = dialogView.findViewById(R.id.shortcut_icon)
        shortcutIcon.setImageBitmap(logo)
        shortcutIcon.setOnClickListener {
            // 启动图片选择器
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            iconLauncher.launch(intent)
        }
        // 显示弹窗
        val alertDialog = alertDialogBuilder.setView(dialogView).create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(0))
        alertDialog.show()
        // 确定按钮
        val shortcutConfirm = dialogView.findViewById<Button>(R.id.shortcut_confirm)
        shortcutConfirm.setOnClickListener {
            val newName = nameInput.text.toString()
            if (newName.isNotEmpty()) {
                addShortcut(newName, url, shortcutLogo, full)
            }
            alertDialog.dismiss()
        }
    }
}
