/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
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

package com.github.shadowsocks.database

import java.net.{URL, URLEncoder}
import java.util.Locale

import android.util.Base64
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.CloseUtils.autoClose
import com.github.shadowsocks.utils.Parser
import com.j256.ormlite.field.{DataType, DatabaseField}

object SSRSub {

  def getResponseString (response: okhttp3.Response): String = {
    var subscribes = ""
    val contentType = response.header("content-type", null)
    if (contentType != null && contentType.contains("application/octet-stream")) {
      autoClose(response.body().byteStream())(in => {
        subscribes = scala.io.Source.fromInputStream(in).mkString
        subscribes = new String(Base64.decode(subscribes, Base64.URL_SAFE))
      })
    } else {
      subscribes = new String(Base64.decode(response.body().string, Base64.URL_SAFE))
    }
    subscribes
  }

  def createSSRSub(responseString: String, requestURL: String): Option[SSRSub] = {
    val profiles_ssr = Parser.findAll_ssr(responseString).toList
    if(profiles_ssr.nonEmpty && profiles_ssr.head.url_group != "") {
      val ssrsub = new SSRSub {
        url = requestURL
        url_group = profiles_ssr(0).url_group
      }
      return Some(ssrsub)
    } else {
      val profiles_vmess = Parser.findAllVmess(responseString).toList
      if (profiles_vmess.nonEmpty) {
        val ssrsub = new SSRSub {
          url = requestURL
          url_group = new URL(requestURL).getHost
        }
        return Some(ssrsub)
      }
    }
    None
  }
  }

// TODO: TIME
class SSRSub {
  @DatabaseField(generatedId = true)
  var id: Int = 0

  @DatabaseField
  var url: String = ""

  @DatabaseField
  var url_group: String = ""
}
