package ddd.pwa.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity


class LauncherShortcutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra("mode") && intent.hasExtra("url")) {
            val newIntent = createIntent(intent)
            if (newIntent != null) {
                newIntent.putExtra("mode", intent.getIntExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue))
                newIntent.putExtra("url", intent.getStringExtra("url"))
                newIntent.putExtra("name", intent.getStringExtra("name"))
                newIntent.putExtra("full", intent.getBooleanExtra("full", true))
                startActivity(newIntent)
            }
        }
        finish()
    }

    private fun createIntent(i: Intent): Intent? {
        val intent = Intent(applicationContext, WebViewActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        // 检查当前是否已经存在该Activity的实例
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (appTask in activityManager.appTasks) {
            val recentTaskInfo = appTask.taskInfo
            val subTaskIntent = recentTaskInfo.baseIntent
            if (subTaskIntent.component == intent.component) {
                // 已经存在该Activity的任务，检查ID是否相同
                if (subTaskIntent.hasExtra("url") && subTaskIntent.getStringExtra("url") == i.getStringExtra("url")) {
                    // ID相同，不需要创建新的任务
                    Log.e("createIntent", "ID相同，不需要创建新的任务")
                    appTask.moveToFront()
                    return null
                }
            }
        }
        // 不存在该Activity的实例，需要创建新的任务
        Log.e("createIntent", "不存在该Activity的实例，需要创建新的任务")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        return intent
    }
}
