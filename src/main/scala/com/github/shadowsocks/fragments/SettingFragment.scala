package com.github.shadowsocks.fragments

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.{CheckBoxPreference, ListPreference, PreferenceFragment, PreferenceManager}
import android.util.Log
import com.github.shadowsocks.{R, SettingActivity, Shadowsocks}
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.preferences.DropDownPreference


class SettingFragment extends PreferenceFragment with OnSharedPreferenceChangeListener {
  lazy val sortMethod = findPreference(Key.SORT_METHOD).asInstanceOf[DropDownPreference]
  lazy val hideServer = findPreference(Key.HIDE_SERVER).asInstanceOf[CheckBoxPreference]
  lazy val autoUpdate = findPreference(Key.AUTO_UPDATE_SUBSCRIPTION).asInstanceOf[CheckBoxPreference]
  lazy val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
  private def activity = getActivity.asInstanceOf[SettingActivity]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.pref_setting)
    sortMethod.setDropDownWidth(R.dimen.default_dropdown_width)
    sortMethod.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putString(Key.SORT_METHOD, value.asInstanceOf[String]).apply()
      true
    })

    hideServer.setOnPreferenceChangeListener((_, value) => {
      prefs.edit().putBoolean(Key.HIDE_SERVER, value.asInstanceOf[Boolean]).apply()
      true
    })

    autoUpdate.setOnPreferenceChangeListener((_, value) => {
      val autoUpdateValue = value.asInstanceOf[Boolean]
      prefs.edit().putBoolean(Key.AUTO_UPDATE_SUBSCRIPTION, autoUpdateValue).apply()
      prefs.edit().putInt(Key.ssrsub_autoupdate, if (autoUpdateValue) 1 else 0).apply()
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
