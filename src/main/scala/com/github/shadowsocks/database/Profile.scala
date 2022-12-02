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
import tun2socks.{Shadowsocks, Trojan, Tun2socks, Vmess, Vless}

import scala.language.implicitConversions
import Profile._
import android.text.TextUtils
import com.github.shadowsocks.R
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.{Key, Route, Utils}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Random, Try}
import ProfileConverter._
import android.net.Uri
import com.github.shadowsocks.database.VmessAction.profile
import com.github.shadowsocks.types.{FailureConnect, Result, SuccessConnect, SuccessSpeed}
// automatic from Android without pc

object Profile {

  def getOption (profile: Profile): String = {
    val routeMode = math.max(Route.ALL_ROUTES.indexOf(profile.route), 0)
    val (dns_address, dns_port, china_dns_address, china_dns_port) =  profile.getDNSConf()
    val logLevel = app.settings.getString(Key.LOG_LEVEL, "debug")
    val disableDNSCache = if (app.settings.getString(Key.SSR_DNS_NOCAHCE, "on") == "off") true else false
    val vmessOption =
      s"""
         |{
         |"useIPv6": ${profile.ipv6},
         |"logLevel":"$logLevel",
         |"enableSniffing": ${profile.enable_domain_sniff},
         |"dns": "$dns_address:$dns_port,$china_dns_address:$china_dns_port",
         |"routeMode": $routeMode,
         |"disableDNSCache": $disableDNSCache,
         |"mux": ${app.settings.getInt(Key.MUX, -1)},
         |"serverName": "${profile.host}",
         |"allowInsecure": true
         |}
""".stripMargin
    vmessOption
  }

  // convert to tun2socks type
  implicit def profileToVmess(profile: Profile): Vmess = {
    if (!profile.isVmess && !profile.isShadowSocks) {
      throw new Exception("Not a V2ray or ShadowSocks Profile")
    }
    val v_security = if (TextUtils.isEmpty(profile.v_security)) "auto" else profile.v_security
    val vmessOption = getOption(profile)
        Log.e("Profile", s"v_host: ${profile.v_host}, v_path: ${profile.v_path}, v_tls: ${profile.v_tls}, v_add: ${profile.v_add},v_port: ${profile.v_port}, v_aid: ${profile.v_aid}, " +
      s"v_net: ${profile.v_net}, v_id: ${profile.v_id}, v_type: ${profile.v_type}, v_security: ${profile.v_security}, useIPv6: ${profile.ipv6}" + s"vmessOption: $vmessOption, domainSniff: ${profile.enable_domain_sniff}")
    if (profile.isShadowSocks) {
      return Tun2socks.newLiteShadowSocks(
        profile.v_add,
        profile.v_port.toLong,
        profile.v_id,
        v_security,
        vmessOption.getBytes(StandardCharsets.UTF_8)
      )
    }
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
      vmessOption.getBytes(StandardCharsets.UTF_8)
    )
  }

  implicit def profileToVless(profile: Profile): Vless = {
    if (!profile.isVless) {
      throw new Exception("Not a Vless Profile")
    }
    val v_security = if (TextUtils.isEmpty(profile.v_security)) "auto" else profile.v_security
    val vmessOption = getOption(profile)
    Log.e("Profile", s"v_tls: ${profile.v_tls}, v_add: ${profile.v_add},v_port: ${profile.v_port}, encryption: ${profile.v_encryption}, flow: ${profile.v_flow}" +
      s"v_path: ${profile.v_path}, v_host: ${profile.v_host} t_peer: ${profile.t_peer}" +
      s"v_net: ${profile.v_net}, v_id: ${profile.v_id}, v_type: ${profile.v_type}, v_security: ${v_security}, useIPv6: ${profile.ipv6}" + s"vmessOption: $vmessOption, domainSniff: ${profile.enable_domain_sniff}")
    Tun2socks.newVless(
        profile.v_add,
        profile.v_port.toLong,
        profile.v_id,
        profile.v_tls,
        profile.v_type,
        profile.v_encryption,
        profile.v_net,
        profile.v_flow,
        profile.v_security,
        profile.v_path,
        profile.v_host,
        profile.t_peer,
        vmessOption.getBytes(StandardCharsets.UTF_8)
      )
  }

    implicit def profileToTrojan(profile: Profile): Trojan = {
    if (!profile.isTrojan) {
      throw new Exception("Not a Trojan Profile")
    }
    val trojanOption = getOption(profile)
    Log.e("Profile", s"${profile.t_addr}, ${profile.t_port}, ${profile.t_password}, ${profile.t_peer}, net: ${profile.v_net}, path: ${profile.v_path}, host: ${profile.v_host}, ${profile.t_allowInsecure}")
    Tun2socks.newTrojan(
      profile.t_addr,
      profile.t_port,
      profile.t_password,
      profile.t_peer,
      profile.t_allowInsecure, // SkipCertVerify
      profile.v_net,
      profile.v_path,
      profile.v_host,
      trojanOption.getBytes(StandardCharsets.UTF_8)
    )
  }

  implicit def profileToShadowsocks(profile: Profile): Shadowsocks = {
    if (!profile.isShadowSocks) {
      throw new Exception("Not a Shadowsocks Profile")
    }
    val shadowsocksOption = getOption(profile)
    Log.e("Profile", s"v_host: ${profile.v_host}, v_path: ${profile.v_path}, v_tls: ${profile.v_tls}, v_add: ${profile.v_add},v_port: ${profile.v_port}, v_aid: ${profile.v_aid}, " +
      s"v_net: ${profile.v_net}, v_id: ${profile.v_id}, v_type: ${profile.v_type}, v_security: ${profile.v_security}, useIPv6: ${profile.ipv6}" + s"vmessOption: $shadowsocksOption, domainSniff: ${profile.enable_domain_sniff}")
    Log.e("Profile", s"Shadowsocks: ${profile.v_add}, ${profile.v_port}, ${profile.v_id}, ${profile.v_security}")
    Tun2socks.newShadowSocks(
      profile.v_add,
      profile.v_port.toLong,
      profile.v_id,
      profile.v_security,
      shadowsocksOption.getBytes(StandardCharsets.UTF_8)
    )
  }

  def profileToBytes(profile: Profile) = ???

  // single ping
  implicit class LatencyTest(profile: Profile) {

    def pingItem: PartialFunction[String, Future[Result[Long]]] = {
      case "tcp" => this.testTCPLatency()
      case "google" => this.testLatency()
    }

    def testLatency(): Future[Result[Long]] = {
      val port = new Random().nextInt(24) + 8900
      Future(profile.getElapsed())
        .map(SuccessConnect)
        .recoverWith {
          case _: Exception => Future(profile.getElapsed(port)).map(SuccessConnect).recover{
              case e: Exception => FailureConnect(e.getMessage)
            }
        }
    }

    def testTCPLatency(): Future[Result[Long]] = {
      Future{
        var profileAddr = this.getAddr()
        val profilePort = this.getPort()
        // Log.e("testTCPPingLatency", s"profileAddr: $profileAddr, profilePort: $profilePort")
        Utils.resolve(profileAddr, enableIPv6 = false) match {
          case Some(addr) => profileAddr = addr
          case None => throw new IOException("Host Not Resolved")
        }
        Tun2socks.testTCPPing(profileAddr, profilePort)
      }.map(SuccessConnect)
        .recover {
          case e: Exception => {
            e.printStackTrace()
            FailureConnect(e.getMessage)
          }
        }
    }

    def testDownload(cb: tun2socks.TestLatencyStop ): Future[Result[Long]] = {
      Future{
        Tun2socks.testLinkDownloadSpeed(profile.toString, cb)
      }.map(SuccessSpeed)
        .recover {
          case e: Exception => {
            e.printStackTrace()
            FailureConnect(e.getMessage)
          }
        }
    }

    def pingItemThread: PartialFunction[(String, Long), String] = {
      case ("tcp", _) => this.testTCPLatencyThread()
      case ("google", port) => this.testLatencyThread(port)
    }

    def testLatencyThread(port: Long): String = {
      Try(profile.getElapsed(port))
        .map(SuccessConnect)
        .recover {
          case e: Exception => {
//            e.printStackTrace()
            FailureConnect(e.getMessage)
          }
        }.map(result => {
        profile.elapsed = result.data
        app.profileManager.updateProfile(profile)
        result.msg
      }).get
    }

    def getAddr (): String = {
      profile match {
        case p if p.isV2Ray => profile.v_add
        case p if p.isTrojan => profile.t_addr
        case _ => profile.host
      }
    }

    // fix conflict
    def getPort (): Int = {
      profile match {
        case p if p.isV2Ray => profile.v_port.toInt
        case p if p.isTrojan => profile.t_port
        case _ => profile.remotePort
      }
    }

    def getPortStr (): String = this.getPort().toString


    // support ssr & v2ray
    // TODO: refactor PROFILE.RESOLVE
    def testTCPLatencyThread () : String  = {
      var profileAddr = this.getAddr()
      val profilePort = this.getPort()
      // Log.e("testTCPPingLatency", s"profileAddr: $profileAddr, profilePort: $profilePort")
      Utils.resolve(profileAddr, enableIPv6 = false) match {
        case Some(addr) => profileAddr = addr
        case None => throw new IOException("Host Not Resolved")
      }
      Try(Tun2socks.testTCPPing(profileAddr, profilePort))
        .map(SuccessConnect)
        .recover {
          case e: Exception => {
            e.printStackTrace()
            FailureConnect(e.getMessage)
          }
        }.map(result => {
        profile.elapsed = result.data
        app.profileManager.updateProfile(profile)
        result.msg
      }).get
    }

    def getDNSConf() : (String, Int, String, Int) = {
      var dns_address = ""
      var dns_port = 53
      var china_dns_address = ""
      var china_dns_port = 53
      try {
        val dns = scala.util.Random.shuffle(profile.dns.split(",").toList).head
        dns_address = dns.split(":")(0)
        dns_port = dns.split(":")(1).toInt

        val china_dns = scala.util.Random.shuffle(profile.china_dns.split(",").toList).head
        china_dns_address = china_dns.split(":")(0)
        china_dns_port = china_dns.split(":")(1).toInt
      } catch {
        case ex: Exception =>
          dns_address = "8.8.8.8"
          dns_port = 53
          china_dns_address = "223.5.5.5"
          china_dns_port = 53
      }
      (dns_address, dns_port, china_dns_address, china_dns_port)
    }
  }
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
  var route: String = "bypass-lan-china"

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
  var dns: String = "8.8.8.8:53,1.1.1.1:53"

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
  var download_speed: Long = 0

  @DatabaseField
  val date: java.util.Date = new java.util.Date()

  @DatabaseField
  var userOrder: Long = _

  @DatabaseField
  var proxy_protocol: String = "ssr" // ssr shadowsocks vmess v2ray_json trojan vless

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

  @DatabaseField
  var v_encryption: String = ""  // vless.vnext.user

  @DatabaseField
  var v_flow: String = "" // vless.vnext.user

  @DatabaseField
  var t_addr: String = ""

  @DatabaseField
  var t_port:Int = 443

  @DatabaseField
  var t_password:String = ""

  @DatabaseField
  var t_allowInsecure: Boolean = false // verify  TODO: check is numeric

  @DatabaseField
  var t_peer: String = ""

  var enable_domain_sniff = true

  override def toString(): String = {
    implicit val flags: Int = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP
    this match {
      case _ if isVmess => VmessQRCode(v_v, v_ps, v_add, v_port, v_id, v_aid, v_net, v_type, v_host, v_path, v_tls, v_security, null, v_security, url_group, t_allowInsecure).toString
      case _ if isV2RayJSON => "vjson://" + Utils.b64Encode(v_json_config.getBytes(Charset.forName("UTF-8")))
      case _ if isTrojan => s"trojan://$t_password@$t_addr:$t_port?sni=$t_peer&allowInsecure=${if(t_allowInsecure) 1 else 0}&type=${v_net}&path=${Uri.encode(v_path)}&host=${v_host}#${Uri.encode(name)}"
      case _ if isVless => s"vless://$v_id@$v_add:$v_port?security=$v_tls&encryption=$v_security&headerType=$v_type&type=$v_net#${Uri.encode(name)}"
      case _ if isShadowSocks => {
        implicit val flags: Int = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP
        val data = s"${v_security}:${v_id}".getBytes(Charset.forName("UTF-8"))
        s"ss://${Utils.b64Encode(data)}@${v_add}:${v_port}#${Uri.encode(name)}"
      }
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

  def isVmess = this.proxy_protocol == "vmess"

  def isV2RayJSON = this.proxy_protocol == "v2ray_json"

  def isV2Ray = isVmess || isV2RayJSON

  def isTrojan = this.proxy_protocol == "trojan"

  def isVless = this.proxy_protocol == "vless"

  def isShadowSocks = this.proxy_protocol == "shadowsocks"

  def isSSR = this.proxy_protocol == "ssr"

  def isV2RayShadowSocks = isShadowSocks && this.v_security == Key.AES_256_GCM

  // shadowSocks aead
  def isAEAD = isV2RayShadowSocks || this.v_security == Key.AES_128_GCM || this.v_security == Key.AES_192_GCM || this.v_security == Key.CHACHA20_IETF_POLY1305 || this.v_security == Key.XCHACHA20_IETF_POLY1305

}
