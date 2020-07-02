package com.github.shadowsocks.fragments

import java.io.{BufferedReader, InputStreamReader}
import java.util.Locale

import android.content.{Intent, SharedPreferences}
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import android.preference.{CheckBoxPreference, ListPreference, Preference, PreferenceFragment, PreferenceManager}
import android.support.v7.app.AlertDialog
import android.util.Log
import android.webkit.{WebView, WebViewClient}
import android.widget.EditText
import com.github.shadowsocks.{BuildConfig, R, SettingActivity, Shadowsocks}
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.ShadowsocksSettings.TAG
import com.github.shadowsocks.preferences.DropDownPreference
import tun2socks.Tun2socks

import scala.collection.mutable


class SettingFragment extends PreferenceFragment with OnSharedPreferenceChangeListener {
//  lazy val sortMethod = findPreference(Key.SORT_METHOD).asInstanceOf[DropDownPreference]
  lazy val pingMethod = findPreference(Key.PING_METHOD).asInstanceOf[DropDownPreference]
  lazy val hideServer = findPreference(Key.HIDE_SERVER).asInstanceOf[CheckBoxPreference]
//  lazy val fullTestBg = findPreference(Key.FULL_TEST_BG).asInstanceOf[CheckBoxPreference]
  lazy val autoUpdate = findPreference(Key.AUTO_UPDATE_SUBSCRIPTION).asInstanceOf[CheckBoxPreference]
  lazy val autoTestConnectivity = findPreference(Key.AUTO_TEST_CONNECTIVITY).asInstanceOf[CheckBoxPreference]
  lazy val ssrDNSNoCache = findPreference(Key.SSR_DNS_NOCAHCE).asInstanceOf[DropDownPreference]
  lazy val aboutPref = findPreference("about")
  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
  private def activity = getActivity.asInstanceOf[SettingActivity]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.pref_setting)
//    sortMethod.setDropDownWidth(R.dimen.default_dropdown_width)
//    sortMethod.setOnPreferenceChangeListener((_, value) => {
//      prefs.edit().putString(Key.SORT_METHOD, value.asInstanceOf[String]).apply()
//      true
//    })

    pingMethod.setDropDownWidth(R.dimen.default_dropdown_width)
    pingMethod.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putString(Key.PING_METHOD, value.asInstanceOf[String]).apply()
      true
    })

    hideServer.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putBoolean(Key.HIDE_SERVER, value.asInstanceOf[Boolean]).apply()
      true
    })

//    fullTestBg.setOnPreferenceChangeListener((_, value) => {
//      prefs.edit().putBoolean(Key.FULL_TEST_BG, value.asInstanceOf[Boolean]).apply()
//      true
//    })

    autoUpdate.setOnPreferenceChangeListener((_, value) => {
      val autoUpdateValue = value.asInstanceOf[Boolean]
      prefs.edit().putBoolean(Key.AUTO_UPDATE_SUBSCRIPTION, autoUpdateValue).apply()
      prefs.edit().putInt(Key.ssrsub_autoupdate, if (autoUpdateValue) 1 else 0).apply()
      true
    })

    autoTestConnectivity.setOnPreferenceChangeListener((_, value) => {
      val autoTestEnabled = value.asInstanceOf[Boolean]
      prefs.edit().putBoolean(Key.AUTO_TEST_CONNECTIVITY, autoTestEnabled).apply()
      true
    })

    ssrDNSNoCache.setDropDownWidth(R.dimen.default_dropdown_width)
    ssrDNSNoCache.setOnPreferenceChangeListener((_, value) => {
      val nocache = value.asInstanceOf[String]
      prefs.edit().putString(Key.SSR_DNS_NOCAHCE, nocache).apply()
      app.appStateManager.saveDNSNoCache(nocache)
      true
    })

    findPreference("ignore_battery_optimization").setOnPreferenceClickListener((preference: Preference) => {
      activity.ignoreBatteryOptimization()
      true
    })

    aboutPref.setSummary(s"v${BuildConfig.VERSION_NAME}")
    aboutPref.setOnPreferenceClickListener(_ => {
      val web = new WebView(activity)
      web.loadUrl("file:///android_asset/pages/about.html")
      web.setWebViewClient(new WebViewClient() {
        override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean = {
          try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          } catch {
            case _: android.content.ActivityNotFoundException => // Ignore
          }
          true
        }
      })

      new AlertDialog.Builder(activity)
        .setTitle(getString(R.string.about_title).formatLocal(Locale.ENGLISH, BuildConfig.VERSION_NAME))
        .setNegativeButton(getString(android.R.string.ok), null)
        .setView(web)
        .create()
        .show()
      true
    })

    findPreference("logcat").setOnPreferenceClickListener(_ => {
      val et_logcat = new EditText(activity)

      try {
        val lst = new mutable.LinkedHashSet[String]()
        lst.add("logcat")
        lst.add("-d")
        lst.add("-v")
        lst.add("time")
        lst.add("com.xxf098.ssrray")
        val logcat = Runtime.getRuntime.exec(lst.toArray)
        val br = new BufferedReader(new InputStreamReader(logcat.getInputStream()))
        var line = ""
        line = br.readLine()
        while (line != null) {
          et_logcat.append(line)
          et_logcat.append("\n")
          line = br.readLine()
        }
        br.close()
      } catch {
        case e: Exception =>  // unknown failures, probably shouldn't retry
          e.printStackTrace()
      }

      new AlertDialog.Builder(activity)
        .setTitle("Logcat")
        .setNegativeButton(getString(android.R.string.ok), null)
        .setView(et_logcat)
        .create()
        .show()
      true
    })

  }


  override def onResume(): Unit = {
    super.onResume()
    prefs.registerOnSharedPreferenceChangeListener(this)
  }


  override def onPause(): Unit = {
    super.onPause()
    prefs.unregisterOnSharedPreferenceChangeListener(this)
  }

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) = {
      activity.isPreferenceChanged = true
  }

}
