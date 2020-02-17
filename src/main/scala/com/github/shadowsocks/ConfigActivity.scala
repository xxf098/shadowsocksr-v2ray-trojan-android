package com.github.shadowsocks

import java.io.File
import java.lang.Exception

import android.app.TaskStackBuilder
import android.os.Bundle
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
import com.github.shadowsocks.fragments.V2RayConfigFragment
import com.google.gson.{Gson, GsonBuilder, JsonParser}
import go.Seq
import org.json.JSONObject
import tun2socks.Tun2socks

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class ConfigActivity extends AppCompatActivity{

  private final val TAG = "ConfigActivity"
  private var etConfig: EditText = _
  private var profile: Profile = _
  var toolbar: Toolbar = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Seq.setContext(getApplicationContext)
    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_config)

    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.v2ray_config)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => {
      val intent = getParentActivityIntent
      if (shouldUpRecreateTask(intent) || isTaskRoot)
        TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()
      else finish()
    })
    // start fragment
    val v2rayConfigFragment = new V2RayConfigFragment()
    val bundle = new Bundle()
    bundle.putInt(Key.EXTRA_PROFILE_ID, getIntent.getIntExtra(Key.EXTRA_PROFILE_ID, -1))
    v2rayConfigFragment.setArguments(bundle)
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.config_fragment_holder, v2rayConfigFragment)
      .commitAllowingStateLoss()

  }

  def saveConfig(config: String): Unit = {
    val future = checkConfig(config)
    future onSuccess {
      case prettyConfig if prettyConfig != null => {
        runOnUiThread(() => {
          val newProfile = Parser.getV2RayJSONProfile(prettyConfig)
          if (profile == null) {
            profile = app.profileManager.createProfile(newProfile)
          } else {
            newProfile.id = profile.id
            newProfile.url_group = profile.url_group
            newProfile.name = profile.name
            app.profileManager.updateProfile(newProfile)
          }
          Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        })
      }
      case _ => runOnUiThread(() => Toast.makeText(this, "Config is not valid!", Toast.LENGTH_SHORT))
    }
    future onFailure {
      case e: Exception => {
        e.printStackTrace()
        runOnUiThread(() => Toast.makeText(this, "config is not valid!", Toast.LENGTH_SHORT).show())
      }
    }
  }

  def checkConfig(config: String): Future[String] = {
      Future {
        val jsonObject = new JsonParser().parse(config).getAsJsonObject
//        val outbounds = jsonObject.getAsJsonArray("outbounds")
//        val vmess = outbounds.get(0).getAsJsonObject
//        val settings = vmess.getAsJsonObject("settings")
//        val vnext = vmess.getAsJsonArray("vnext")
//        Log.e(TAG, vmess.toString)
//        Log.e(TAG, "protocol==")
        val prettyConfig = new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject)
//        val assetPath = getApplicationInfo.dataDir + "/files/"
//        Tun2socks.testConfig(prettyConfig, assetPath)
        prettyConfig
      }
  }
}
