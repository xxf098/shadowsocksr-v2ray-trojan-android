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

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.{Charset, StandardCharsets}
import java.util.Locale

import android.util.{Base64, Log}
import com.google.gson.GsonBuilder
import com.j256.ormlite.field.{DataType, DatabaseField}
import tun2socks.{Tun2socks, Vmess}

import scala.language.implicitConversions
import Profile._
import android.text.TextUtils
import com.github.shadowsocks.R
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.Utils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import ProfileMixin._
// automatic from Android without pc

object Profile {
  implicit def profileToVmess(profile: Profile): Vmess = {
    if (!profile.isVmess) {
      throw new Exception("Not a V2ray Profile")
    }
    val v_security = if (TextUtils.isEmpty(profile.v_security)) "auto" else profile.v_security
    Tun2socks.newVmess(
      profile.v_host,
      profile.v_path,
      profile.v_tls,
      profile.v_add,
      profile.v_port.toLong,
      profile.v_aid.toLong,
      profile.v_net,
      profile.v_id,
      profile.v_type,
      v_security,
      "error"
    )
  }

  // TODO:
  def profileToBytes(profile: Profile) = ???
}

class Profile {
  @DatabaseField(generatedId = true)
  var id: Int = 0

  @DatabaseField
  var name: String = "Untitled"

  @DatabaseField
  var host: String = ""

  @DatabaseField
  var localPort: Int = 1080

  @DatabaseField
  var remotePort: Int = 8388

  @DatabaseField
  var password: String = ""

  @DatabaseField
  var protocol: String = "origin"

  @DatabaseField
  var protocol_param: String = ""

  @DatabaseField
  var obfs: String = "plain"

  @DatabaseField
  var obfs_param: String = ""

  @DatabaseField
  var method: String = "aes-256-cfb"

  @DatabaseField
  var route: String = "all"

  @DatabaseField
  var proxyApps: Boolean = false

  @DatabaseField
  var bypass: Boolean = false

  @DatabaseField
  var udpdns: Boolean = false

  @DatabaseField
  var url_group: String = ""

  @DatabaseField
  var ssrsub_id: Int = 0

  @DatabaseField
  var dns: String = "1.1.1.1:53,8.8.8.8:53"

  @DatabaseField
  var china_dns: String = "223.5.5.5:53,223.6.6.6:53"

  @DatabaseField
  var ipv6: Boolean = false

  @DatabaseField(dataType = DataType.LONG_STRING)
  var individual: String = ""

  @DatabaseField
  var tx: Long = 0

  @DatabaseField
  var rx: Long = 0

  @DatabaseField
  var elapsed: Long = 0

  @DatabaseField
  val date: java.util.Date = new java.util.Date()

  @DatabaseField
  var userOrder: Long = _

  @DatabaseField
  var proxy_protocol: String = "ssr" // ssr vmess v2ray_json

  @DatabaseField
  var v_v: String = "2"

  @DatabaseField
  var v_ps: String = ""

  @DatabaseField
  var v_add: String = ""

  @DatabaseField
  var v_port: String = ""

  @DatabaseField
  var v_id: String = ""

  @DatabaseField
  var v_aid: String = ""

  @DatabaseField
  var v_net: String = ""

  @DatabaseField
  var v_type: String = ""

  @DatabaseField
  var v_host: String = ""

  @DatabaseField
  var v_path: String = ""

  @DatabaseField
  var v_tls: String = ""

  @DatabaseField
  var v_json_config: String = ""

  @DatabaseField
  var v_security: String = ""

  override def toString(): String = {
    implicit val flags: Int = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP
    this match {
      case _ if isVmess => VmessQRCode(v_v, v_ps, v_add, v_port, v_id, v_aid, v_net, v_type, v_host, v_path, v_tls, url_group).toString
      case _ if isV2RayJSON => "vjson://" + Utils.b64Encode(v_json_config.getBytes(Charset.forName("UTF-8")))
      case _ => "ssr://" + Utils.b64Encode("%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s&group=%s".formatLocal(Locale.ENGLISH,
        host, remotePort, protocol, method, obfs,
        Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, password).getBytes),
        Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, obfs_param).getBytes),
        Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, protocol_param).getBytes),
        Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, name).getBytes),
        Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, url_group).getBytes)).getBytes)
    }
  }

  def isMethodUnsafe = "table".equalsIgnoreCase(method) || "rc4".equalsIgnoreCase(method)

  // to ADT
  def isVmess = this.proxy_protocol == "vmess"

  def isV2RayJSON = this.proxy_protocol == "v2ray_json"

  def isV2Ray = isVmess || isV2RayJSON

  def testLatency (): Future[Long] = {
    Future(this.getElapsed())
    .map(elapsed => {
      this.elapsed = elapsed
      app.profileManager.updateProfile(this)
      elapsed
    })
  }

  def testLatencyThread () : String = {
    Try(this.getElapsed()).map(elapsed => {
      this.elapsed = elapsed
      app.profileManager.updateProfile(this)
      app.getString(R.string.connection_test_available, elapsed: java.lang.Long)
    }).recover{
      case e: Exception => app.getString(R.string.connection_test_error, e.getMessage)
    }.get
  }

}
