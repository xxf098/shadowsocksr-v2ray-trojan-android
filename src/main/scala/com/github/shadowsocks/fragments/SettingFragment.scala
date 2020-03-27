package com.github.shadowsocks.fragments

import android.os.Bundle
import android.preference.{CheckBoxPreference, ListPreference, PreferenceFragment, PreferenceManager}
import com.github.shadowsocks.{R, SettingActivity, Shadowsocks}
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.preferences.DropDownPreference


class SettingFragment extends PreferenceFragment {
  lazy val sortMethod = findPreference(Key.SORT_METHOD).asInstanceOf[DropDownPreference]
  lazy val hideServer = findPreference(Key.HIDE_SERVER).asInstanceOf[CheckBoxPreference]
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
  }

}
