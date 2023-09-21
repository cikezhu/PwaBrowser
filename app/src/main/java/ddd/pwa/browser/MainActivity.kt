package ddd.pwa.browser

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    private val mTAG: String = "MainActivity"
    private val mScope = MainScope()
    private val launcher = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            Log.e(mTAG, data.toString())
            val url = data!!.getStringExtra("url")
            val name = data.getStringExtra("name")
            val color = data.getIntExtra("color", R.color.logo_bg)
            val logo = data.getParcelableExtra<Bitmap>("logo")
            if (url !== null && name !== null && logo !== null) {
                addShortcut(name, url, color, logo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 绑定组件
        val myInputUrl: EditText = findViewById(R.id.input_url)
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
                launcher.launch(intent)
            } else {
                Toast.makeText(applicationContext,"请输入网址> http(s)://....",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addShortcut(name: String, url: String, color: Int, logo: Bitmap) {
        // 创建快捷方式的Intent
        val shortcutIntent = Intent(applicationContext, WebViewActivity::class.java)
        shortcutIntent.action = Intent.ACTION_MAIN
        shortcutIntent.putExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue)
        shortcutIntent.putExtra("url", url);
        shortcutIntent.putExtra("color", color);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 创建快捷方式
        val shortcutInfo = ShortcutInfo.Builder(applicationContext, url)
            .setShortLabel(name)
            .setIcon(Icon.createWithBitmap(logo))
            .setIntent(shortcutIntent)
            .build()
        // 添加快捷方式到桌面
        mScope.launch(Dispatchers.Main) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            shortcutManager.requestPinShortcut(shortcutInfo, null)
        }
    }

}
