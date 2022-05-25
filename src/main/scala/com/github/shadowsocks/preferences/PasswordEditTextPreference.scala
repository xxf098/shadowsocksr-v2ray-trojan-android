/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2013 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package com.github.shadowsocks.preferences

import android.content.Context
import android.os.Build
import android.preference.EditTextPreference
import android.util.{AttributeSet, Log}
import android.view.View
import android.widget.EditText
import com.github.shadowsocks.R

class PasswordEditTextPreference(context: Context, attrs: AttributeSet, defStyle: Int)
  extends EditTextPreference(context, attrs, defStyle) {

  var txtPassword = ""
  var etPassword: EditText = _

  def this(context: Context, attrs: AttributeSet) = {
    this(context, attrs, android.R.attr.editTextPreferenceStyle)
    mDefaultSummary = getSummary
    if (isAndroid11()) {
        setDialogLayoutResource(R.layout.preference_dialog_password)
    }
  }

  override def setText(text: String) {
    super.setText(text)
    setSummary(text)
    txtPassword = text
  }

  override def onAddEditTextToDialogView(dialogView: View, editText: EditText): Unit = {
    if (isAndroid11()) {
      etPassword = dialogView.findViewById(android.R.id.edit).asInstanceOf[EditText]
      if (etPassword != null) { etPassword.setText(txtPassword) }
    }
    super.onAddEditTextToDialogView(dialogView, editText)
  }

  override def onDialogClosed(positiveResult: Boolean): Unit = {
    if (isAndroid11()) {
      Option(getEditText).foreach(_.setText(etPassword.getText))
    }
    super.onDialogClosed(positiveResult)
  }

  override def setSummary(summary: CharSequence) {
    if (summary.toString.isEmpty) {
      super.setSummary(mDefaultSummary)
    } else {
      super.setSummary("********")
    }
  }

  def isAndroid11(): Boolean = {
    Build.VERSION.SDK_INT < 31
  }

  private var mDefaultSummary: CharSequence = getSummary
}
