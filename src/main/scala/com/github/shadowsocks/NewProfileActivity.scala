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
import com.github.shadowsocks.fragments.{SubscriptionFragment, V2RayConfigFragment}
import com.google.gson.{Gson, GsonBuilder, JsonParser}
import go.Seq
import org.json.JSONObject
import tun2socks.Tun2socks

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


// PreferenceFragmentCompat
class NewProfileActivity extends AppCompatActivity{

  private final val TAG = "NewProfileActivity"
  var toolbar: Toolbar = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Seq.setContext(getApplicationContext)
    setContentView(R.layout.layout_new_profile)

    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.NEW)
    toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
    toolbar.setNavigationOnClickListener(_ => onBackPressed())

  }
}
