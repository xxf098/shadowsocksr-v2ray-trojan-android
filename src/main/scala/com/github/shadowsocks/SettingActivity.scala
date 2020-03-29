package com.github.shadowsocks

import java.io.File
import java.lang.Exception

import android.app.{Activity, TaskStackBuilder}
import android.content.{ComponentName, Context, Intent}
import android.os.{Bundle, PowerManager}
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.util.Log
import android.view.{MenuItem, WindowManager}
import android.widget.{EditText, TextView, Toast}
import com.github.shadowsocks.utils.Parser.TAG
import com.github.shadowsocks.utils.{ConfigUtils, Key, Parser}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.fragments.{SubscriptionFragment, V2RayConfigFragment}
import com.google.gson.{Gson, GsonBuilder, JsonParser}
import go.Seq
import org.json.JSONObject
import tun2socks.Tun2socks

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class SettingActivity extends AppCompatActivity{

  private final val TAG = "SettingActivity"
  var toolbar: Toolbar = _
  var isPreferenceChanged = false

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Seq.setContext(getApplicationContext)
    //    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    //    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_settings)

    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.settings)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => onBackPressed())
  }

  override def onBackPressed(): Unit = {
    val intent = new Intent(this, classOf[ProfileManagerActivity])
    val resultCode = if (isPreferenceChanged) Activity.RESULT_OK else Activity.RESULT_CANCELED
    setResult(resultCode, intent)
    finish()
  }

  def ignoreBatteryOptimization() {
    // http://blog.csdn.net/laxian2009/article/details/52474214

    var exception = false
    try {
      val powerManager: PowerManager = this.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
      val packageName = this.getPackageName
      val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
      if (!hasIgnored) {
        val intent = new Intent()
        intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.setData(android.net.Uri.parse("package:" + packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
      } else {
        startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
      }
      exception = false
    } catch {
      case _: Throwable =>
        exception = true
    } finally {
    }
    if (exception) {
      try {
        val intent = new Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val cn = new ComponentName(
          "com.android.settings",
          "com.android.com.settings.Settings@HighPowerApplicationsActivity"
        )

        intent.setComponent(cn)
        startActivity(intent)

        exception = false
      } catch {
        case _: Throwable =>
          exception = true
      } finally {
      }
    }
  }
}
