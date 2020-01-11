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

import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale

import android.util.{Base64, Log}
import com.google.gson.GsonBuilder
import com.j256.ormlite.field.{DataType, DatabaseField}
import tun2socks.{ Tun2socks, Vmess }

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
  var dns: String = "1.1.1.1:53,8.8.8.8:53,9.9.9.9:53"

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

  override def toString(): String = {
    if (isVmess) {
      val vmessQRCode = VmessQRCode(
        this.v_v,
        this.v_ps,
        this.v_add,
        this.v_port,
        this.v_id,
        this.v_aid,
        this.v_net,
        this.v_type,
        this.v_host,
        this.v_path,
        this.v_tls)
      val vmessJson = new GsonBuilder().setPrettyPrinting().create().toJson(vmessQRCode)
      Log.e("Profile", vmessJson)
      return "vmess://" + Base64.encodeToString(
        vmessJson.getBytes(Charset.forName("UTF-8")),
        Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP)
    }
    "ssr://" + Base64.encodeToString("%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s&group=%s".formatLocal(Locale.ENGLISH,
      host, remotePort, protocol, method, obfs, Base64.encodeToString("%s".formatLocal(Locale.ENGLISH,
        password).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), Base64.encodeToString("%s".formatLocal(Locale.ENGLISH,
        obfs_param).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), Base64.encodeToString("%s".formatLocal(Locale.ENGLISH,
        protocol_param).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), Base64.encodeToString("%s".formatLocal(Locale.ENGLISH,
        name).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), Base64.encodeToString("%s".formatLocal(Locale.ENGLISH,
        url_group).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP)).getBytes, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP)
  }

  def isMethodUnsafe = "table".equalsIgnoreCase(method) || "rc4".equalsIgnoreCase(method)

  def isVmess = this.proxy_protocol == "vmess"

  def toVmess: Vmess = {
    if (!isVmess) {
      throw new Exception("Not a V2ray Profile")
    }
    Tun2socks.newVmess(
      this.v_host,
      this.v_path,
      this.v_tls,
      this.v_add,
      this.v_port.toLong,
      this.v_aid.toLong,
      this.v_net,
      this.v_id,
      "error"
    )
  }
}
