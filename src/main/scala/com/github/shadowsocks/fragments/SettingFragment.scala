package com.github.shadowsocks.fragments

import android.os.Bundle
import android.preference.PreferenceFragment
import com.github.shadowsocks.R

class SettingFragment extends PreferenceFragment {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.pref_setting)
  }
}
