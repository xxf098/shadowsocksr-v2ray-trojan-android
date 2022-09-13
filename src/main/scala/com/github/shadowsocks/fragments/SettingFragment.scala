package com.github.shadowsocks.fragments

import java.io.{BufferedReader, File, InputStreamReader}
import java.util.Locale

import android.app.ProgressDialog
import android.content.{Intent, SharedPreferences}
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.{Bundle, Handler}
import android.preference.{CheckBoxPreference, EditTextPreference, ListPreference, MultiSelectListPreference, Preference, PreferenceFragment, PreferenceManager}
import android.support.v7.app.AlertDialog
import android.text.InputType
import android.util.Log
import android.view.View
import android.webkit.{WebView, WebViewClient}
import android.widget.{EditText, Toast}
import android.view.ViewGroup.{LayoutParams, MarginLayoutParams}
import com.github.shadowsocks.{BuildConfig, R, SettingActivity, Shadowsocks, V2rayDat}
import com.github.shadowsocks.utils.{Key, Utils}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.ShadowsocksSettings.TAG
import com.github.shadowsocks.preferences.{DropDownPreference, NumberPickerPreference}
import tun2socks.Tun2socks

import scala.collection.mutable
import scala.util.Try


class SettingFragment extends PreferenceFragment with OnSharedPreferenceChangeListener {
//  lazy val sortMethod = findPreference(Key.SORT_METHOD).asInstanceOf[DropDownPreference]
  lazy val pingMethod = findPreference(Key.PING_METHOD).asInstanceOf[ListPreference]
  lazy val selectDisplayInfo = findPreference(Key.SELECT_DISPLAY_INFO).asInstanceOf[MultiSelectListPreference]
  lazy val autoUpdate = findPreference(Key.AUTO_UPDATE_SUBSCRIPTION).asInstanceOf[CheckBoxPreference]
  lazy val autoTestConnectivity = findPreference(Key.AUTO_TEST_CONNECTIVITY).asInstanceOf[CheckBoxPreference]
  lazy val ssrDNSNoCache = findPreference(Key.SSR_DNS_NOCAHCE).asInstanceOf[ListPreference]
  lazy val aboutPref = findPreference("about")
  lazy val updateV2RayDatPref = findPreference("update_v2ray_rules_dat")
  lazy val enableSniffDomain = findPreference(Key.ENABLE_SNIFF_DOMAIN).asInstanceOf[CheckBoxPreference]
  lazy val logLevel = findPreference(Key.LOG_LEVEL).asInstanceOf[ListPreference]
  lazy val v2rayCore = findPreference(Key.V2RAY_CORE).asInstanceOf[ListPreference]
  lazy val mux = findPreference(Key.MUX).asInstanceOf[NumberPickerPreference]
  lazy val testConcurrency = findPreference(Key.TEST_CONCURRENCY).asInstanceOf[NumberPickerPreference]
  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
  private def activity = getActivity.asInstanceOf[SettingActivity]
  private var updateProgressDialog: ProgressDialog = _
  private val handler = new Handler

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.pref_setting)
//    sortMethod.setDropDownWidth(R.dimen.default_dropdown_width)
//    sortMethod.setOnPreferenceChangeListener((_, value) => {
//      prefs.edit().putString(Key.SORT_METHOD, value.asInstanceOf[String]).apply()
//      true
//    })

    pingMethod.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putString(Key.PING_METHOD, value.asInstanceOf[String]).apply()
      true
    })

    selectDisplayInfo.setOnPreferenceChangeListener((_, value) => {
      val info = value.asInstanceOf[java.util.Set[String]]
      prefs.edit().putStringSet(Key.SELECT_DISPLAY_INFO, info).apply()
      true
    })

//    fullTestBg.setOnPreferenceChangeListener((_, value) => {
//      prefs.edit().putBoolean(Key.FULL_TEST_BG, value.asInstanceOf[Boolean]).apply()
//      true
//    })
    mux.setValue(prefs.getInt(Key.MUX, 0))
    mux.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putInt(Key.MUX, value.asInstanceOf[Int]).apply()
      true
    })

    testConcurrency.setValue(prefs.getInt(Key.TEST_CONCURRENCY, 2))
    testConcurrency.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putInt(Key.TEST_CONCURRENCY, value.asInstanceOf[Int]).apply()
      true
    })

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

    // TODO: appStateManager
    enableSniffDomain.setOnPreferenceChangeListener((_, value) => {
      val enabled = value.asInstanceOf[Boolean]
      prefs.edit().putBoolean(Key.ENABLE_SNIFF_DOMAIN, enabled).apply()
      true
    })

    ssrDNSNoCache.setOnPreferenceChangeListener((_, value) => {
      val nocache = value.asInstanceOf[String]
      prefs.edit().putString(Key.SSR_DNS_NOCAHCE, nocache).apply()
      app.appStateManager.saveDNSNoCache(nocache)
      true
    })

    logLevel.setOnPreferenceChangeListener((_, value) => {
      val level = value.asInstanceOf[String]
      prefs.edit().putString(Key.LOG_LEVEL, level).apply()
      true
    })

    v2rayCore.setOnPreferenceChangeListener((_, value) => {
      val core = value.asInstanceOf[String]
      prefs.edit().putString(Key.V2RAY_CORE, core).apply()
      true
    })

    findPreference("ignore_battery_optimization").setOnPreferenceClickListener((preference: Preference) => {
      activity.ignoreBatteryOptimization()
      true
    })

    updateV2RayDatPref.setOnPreferenceClickListener(_ => {
      Toast.makeText(activity, "checking for new updates", Toast.LENGTH_SHORT).show()
      Utils.ThrowableFuture(try {
        handler.post(() => updateProgressDialog = ProgressDialog.show(activity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true))
        val linkSumIP = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat.sha256sum"
        val linkSumSite = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat.sha256sum"
        updateProgressDialog.setMessage("Getting checksum")
        val checksumIP = V2rayDat.getChecksumResponse(linkSumIP).get
        Log.e("pref", checksumIP)
        if (checksumIP.length< 64) { return true }
        val checksumSite = V2rayDat.getChecksumResponse(linkSumSite).get
        Log.e("pref", checksumSite)
        if (checksumIP.length< 64) { return true }
        // compare checksum

        // download file
        val linkIP = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat"
        val linkSite = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat"
        val dataDir = activity.getApplicationInfo.dataDir + "/files/";
        val oldIpPath = s"${dataDir}geoip.dat"
        val ipPath = s"${dataDir}geoip_${checksumIP.substring(0, 64)}.dat"
        val sitePath = s"${dataDir}geosite_${checksumSite.substring(0, 64)}.dat"
        val oldSitePath = s"${dataDir}geosite.dat"
        updateProgressDialog.setMessage("Downloading dat files")
        V2rayDat.downloadDatFile(linkIP,ipPath)
        Log.e("==", s"${ipPath}")
        V2rayDat.downloadDatFile(linkSite, sitePath)
        Log.e("==", s"${sitePath}")
        val paths = Tun2socks.updateV2RayDat()
        Log.e("paths", paths)
        // rename
        V2rayDat.renameDatFile(ipPath, oldIpPath)
        V2rayDat.renameDatFile(sitePath, oldSitePath)
        val paths1 = Tun2socks.updateV2RayDat()
        Log.e("paths", paths1)
        Toast.makeText(activity, "Update success", Toast.LENGTH_SHORT).show()
      } catch {
        case e: Exception => {
          e.printStackTrace()
        }
      } finally {
        handler.post(() => updateProgressDialog.dismiss())
      })
      true
    })

    aboutPref.setSummary(s"SSRRAY: v${BuildConfig.VERSION_NAME}; xray-core: v${Tun2socks.checkXVersion()}")
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
