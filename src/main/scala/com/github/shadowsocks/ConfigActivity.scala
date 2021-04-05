package com.github.shadowsocks

import java.io.File
import java.lang.Exception

import android.app.{Activity, TaskStackBuilder}
import android.content.Intent
import android.os.Bundle
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
import com.github.shadowsocks.fragments.{SubscriptionFragment, V2RayConfigFragment, RouteRuleFragment}
import com.google.gson.{Gson, GsonBuilder, JsonParser}
import go.Seq
import org.json.JSONObject
import tun2socks.Tun2socks

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class ConfigActivity extends AppCompatActivity{

  private final val TAG = "ConfigActivity"
  private var etConfig: EditText = _
  private var profile: Profile = _
  var toolbar: Toolbar = _
  private val nameValues = mutable.Map[String, String]()

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Seq.setContext(getApplicationContext)
//    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
//    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_config)

    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.v2ray_config)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => onBackPressed())
    // start fragment
    val fragmentName = Option(getIntent.getStringExtra(Key.FRAGMENT_NAME))
    navigateToFragment(fragmentName)
  }

  override def onBackPressed(): Unit = {
    val intent = new Intent(this, classOf[ProfileManagerActivity])
    nameValues match {
      case nv if nv.isEmpty => super.onBackPressed()
      case nv => {
        for ((name, value) <- nv) { intent.putExtra(name, value) }
        setResult(Activity.RESULT_OK, intent)
        finish()
      }
    }
  }

  def navigateToFragment (name: Option[String]): Unit = {
    name match {
      case Some(Key.FRAGMENT_V2RAY_CONFIG) | None => {
        val v2rayConfigFragment = new V2RayConfigFragment()
        val bundle = new Bundle()
        bundle.putInt(Key.EXTRA_PROFILE_ID, getIntent.getIntExtra(Key.EXTRA_PROFILE_ID, -1))
        v2rayConfigFragment.setArguments(bundle)
        displayFragment(v2rayConfigFragment)
      }
      case Some(Key.FRAGMENT_ROUTE_RULE) => displayFragment(new RouteRuleFragment())
      case Some(Key.FRAGMENT_SUBSCRIPTION) => displayFragment(new SubscriptionFragment())
      case _ =>
    }
  }

  def displayFragment(fragment: Fragment): Unit = {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.config_fragment_holder, fragment)
      .commitAllowingStateLoss()
  }

  def putStringExtra(name: String, value: String): Unit = {
    nameValues += (name -> value)
  }
}
