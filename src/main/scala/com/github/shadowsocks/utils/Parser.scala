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

package com.github.shadowsocks.utils

import java.net.URLDecoder
import java.util

import android.text.TextUtils
import android.util.{Base64, Log}
import com.github.shadowsocks.database.{DnsBean, InSettingsBean, InboundBean, LogBean, MuxBean, OutSettingsBean, OutboundBean, Profile, RoutingBean, RulesBean, StreamSettingsBean, TlssettingsBean, UsersBean, V2rayConfig, VmessBean, VmessQRCode, VnextBean, WsHeaderBean, WssettingsBean}
import com.google.gson.{Gson, GsonBuilder}

import scala.collection.JavaConverters._

object Parser {
  val TAG = "ShadowParser"
  private val pattern = "(?i)ss://([A-Za-z0-9+-/=_]+)(#(.+))?".r
  private val decodedPattern = "(?i)^((.+?)(-auth)??:(.*)@(.+?):(\\d+?))$".r

  private val pattern_ssr = "(?i)ssr://([A-Za-z0-9_=-]+)".r
  private val decodedPattern_ssr = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):([^/]+))".r
  private val decodedPattern_ssr_obfsparam = "(?i)[?&]obfsparam=([A-Za-z0-9_=-]*)".r
  private val decodedPattern_ssr_remarks = "(?i)[?&]remarks=([A-Za-z0-9_=-]*)".r
  private val decodedPattern_ssr_protocolparam = "(?i)[?&]protoparam=([A-Za-z0-9_=-]*)".r
  private val decodedPattern_ssr_groupparam = "(?i)[?&]group=([A-Za-z0-9_=-]*)".r

  private val pattern_vmess = "(?i)(vmess://[A-Za-z0-9_/+=-]+)".r

  def findAll(data: CharSequence) = pattern.findAllMatchIn(if (data == null) "" else data).map(m => try
    decodedPattern.findFirstMatchIn(new String(Base64.decode(m.group(1), Base64.NO_PADDING), "UTF-8")) match {
      case Some(ss) =>
        val profile = new Profile
        profile.method = ss.group(2).toLowerCase
        if (ss.group(3) != null) profile.protocol = "verify_sha1"
        profile.password = ss.group(4)
        profile.name = ss.group(5)
        profile.host = profile.name
        profile.remotePort = ss.group(6).toInt
        if (m.group(2) != null) profile.name = URLDecoder.decode(m.group(3), "utf-8")
        profile
      case _ => null
    }
    catch {
      case ex: Exception =>
        Log.e(TAG, "parser error: " + m.source, ex)// Ignore
        null
    }).filter(_ != null)

  def findAll_ssr(data: CharSequence) = pattern_ssr.findAllMatchIn(if (data == null) "" else data).map(m => try{
    val uri = new String(Base64.decode(m.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
    decodedPattern_ssr.findFirstMatchIn(uri) match {
          case Some(ss) =>
            val profile = new Profile
            profile.host = ss.group(2).toLowerCase
            profile.remotePort = ss.group(3).toInt
            profile.protocol = ss.group(4).toLowerCase
            profile.method = ss.group(5).toLowerCase
            profile.obfs = ss.group(6).toLowerCase
            profile.password = new String(Base64.decode(ss.group(7).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")

            decodedPattern_ssr_obfsparam.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.obfs_param = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            decodedPattern_ssr_protocolparam.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.protocol_param = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            decodedPattern_ssr_remarks.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.name = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => profile.name = ss.group(2).toLowerCase
            }

            decodedPattern_ssr_groupparam.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.url_group = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            profile
          case _ => null
        }
    }
    catch {
      case ex: Exception =>
        Log.e(TAG, "parser error: " + m.source, ex)// Ignore
        null
    }).filter(_ != null)

  def findAllVmess(data: CharSequence) = pattern_vmess
    .findAllMatchIn(if (data == null) "" else data)
    .flatMap(m => try {
      findVmess(m.group(1).replaceAll("=", ""))
    } catch {
      case ex: Exception =>
        Log.e(TAG, "parser error: " + m.source, ex) // Ignore
        None
    })
    .map(convertVmessBeanToProfile)

  private def convertVmessBeanToProfile (vmessBean: VmessBean): Profile = {
    val profile = new Profile
    profile.proxy_protocol = "vmess"
    profile.v_v = vmessBean.configVersion.toString
    profile.v_ps = if (TextUtils.isEmpty(vmessBean.remarks)) vmessBean.address else vmessBean.remarks
    profile.v_add = vmessBean.address
    profile.v_port = vmessBean.port.toString
    profile.v_id = vmessBean.id
    profile.v_aid = vmessBean.alterId.toString
    profile.v_net = vmessBean.network
    profile.v_type = vmessBean.headerType
    profile.v_host = vmessBean.requestHost
    profile.v_path = vmessBean.path
    profile.v_tls = vmessBean.streamSecurity
    profile.v_security = vmessBean.security
    // common
    profile.name = profile.v_ps
    profile.url_group = vmessBean.url_group
    profile.route = Route.ALL
    profile
  }

  // single link
  def findVmess (vmessLink: String): Option[VmessBean] = {
    if (vmessLink == null ||
      TextUtils.isEmpty(vmessLink) ||
    !vmessLink.startsWith("vmess://")) return None
    val indexSplit = vmessLink.indexOf("?")
    if (indexSplit > 0) return None
    var result = vmessLink.replace("vmess://", "")
        .replace("+", "-")
        .replace("/", "_")
    result = new String(Base64.decode(result, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8")
//    Log.e(TAG, result)
    val vmessQRCode = new Gson().fromJson(result, classOf[VmessQRCode])
    if (TextUtils.isEmpty(vmessQRCode.add) ||
    TextUtils.isEmpty(vmessQRCode.port) ||
    TextUtils.isEmpty(vmessQRCode.id) ||
    TextUtils.isEmpty(vmessQRCode.aid) ||
    TextUtils.isEmpty(vmessQRCode.net)) return None
    val vmess = VmessBean()
    vmess.configType = EConfigType.Vmess
    vmess.security = "auto"
    vmess.network = "tcp"

    vmess.configVersion = Option(vmessQRCode.v).getOrElse("2").toInt
    vmess.remarks = Option(vmessQRCode.ps).getOrElse(vmessQRCode.add)
    vmess.address = vmessQRCode.add
    vmess.port = vmessQRCode.port.toInt
    vmess.id = vmessQRCode.id
    vmess.alterId = vmessQRCode.aid.toInt
    vmess.network = vmessQRCode.net
    vmess.headerType = Option(vmessQRCode.`type`).getOrElse("none")
    vmess.requestHost = Option(vmessQRCode.host).getOrElse("")
    vmess.path = Option(vmessQRCode.path).getOrElse("")
    vmess.streamSecurity = Option(vmessQRCode.tls).getOrElse("")
    vmess.subid = ""
    vmess.url_group = if (TextUtils.isEmpty(vmessQRCode.url_group)) vmess.url_group else vmessQRCode.url_group
    Some(vmess)
  }

  def getV2rayConfig(vmessLink: String): Option[String] = {
    findVmess(vmessLink) match {
      case Some(vmessBean) => Some(getV2rayConfig(vmessBean))
      case None => None
    }
  }

  def getV2rayConfig(profile: Profile) : Option[String] = {
    getV2rayConfig(profile.toString())
  }

  def getV2rayConfig(vmessBean: VmessBean): String = {
    val v2rayConfig = V2rayConfig()
    v2rayConfig.log = LogBean("", "", "error")
    // host
    val host = new util.HashMap[String, String]()
    host.put("domain:googleapis.cn", "googleapis.com")
    Route.BLOCK_DOMAIN.foreach(domain => host.put(domain, "127.0.0.1"))
    v2rayConfig.dns = DnsBean(List("1.0.0.1", "localhost").asJava, host)
    // routing
    val geoipRule = RulesBean()
    geoipRule.ip = List("geoip:private", "geoip:cn").asJava
    val geositeRule = RulesBean()
    geositeRule.domain = List("geosite:cn").asJava
    v2rayConfig.routing = RoutingBean("IPIfNonMatch", List(geoipRule, geositeRule).asJava)
    // inbounds
    val sockInbound = InboundBean("socks-in", 8088, "socks", "::", null, null)
    sockInbound.settings = InSettingsBean("noauth", udp = true, "127.0.0.1")
    val httpInvound = InboundBean("http-in", 8090, "http", "::", null, null)
    v2rayConfig.inbounds = List(sockInbound, httpInvound).asJava
    // outbounds
    val freedomOutbound = OutboundBean("direct", "freedom", OutSettingsBean(null, null, null, "UseIP"), null, null)
    val vmessOutbound = OutboundBean("proxy", "vmess", null, null, MuxBean(true))
    // vnext
    val userBean = UsersBean(vmessBean.id, vmessBean.alterId, vmessBean.security, "v2ray@email.com")
    val vnext = VnextBean(vmessBean.address, vmessBean.port, List(userBean).asJava)
    vmessOutbound.settings = OutSettingsBean(List(vnext).asJava, null, null, null)
    // streamSettings
    if (vmessBean.network == "ws") {
      val streamSettingsBean = StreamSettingsBean(vmessBean.network, null, null, null, null, null, null, null)
      val wssettingsBean = WssettingsBean(connectionReuse = true, vmessBean.path, null)
      if (!TextUtils.isEmpty(vmessBean.requestHost)) {
        wssettingsBean.headers = WsHeaderBean(vmessBean.requestHost)
      }
      streamSettingsBean.wssettings = wssettingsBean
      if (vmessBean.streamSecurity == "tls") {
          streamSettingsBean.security = vmessBean.streamSecurity
        streamSettingsBean.tlssettings = TlssettingsBean()
      }
      vmessOutbound.streamSettings = streamSettingsBean
    }
    v2rayConfig.outbounds = List(vmessOutbound, freedomOutbound).asJava
    val vmessJson = new GsonBuilder().setPrettyPrinting().create().toJson(v2rayConfig)
    Log.e(TAG, vmessJson)
    vmessJson
  }

  def getV2RayJSONProfile (config: String): Profile = {
    val profile = new Profile
    profile.proxy_protocol = "v2ray_json"
    profile.url_group = "v2ray"
    profile.name = "V2ray Config"
    profile.v_ps = profile.name
    profile.v_json_config = config
    profile.v_port = "0"
    profile.v_add = "127.0.0.1"
    profile.v_id = ""
    profile.v_aid = ""
    profile.v_path = ""
    profile.v_host = ""
    "\"address\":\\s*\"(.+)\"".r.findFirstMatchIn(config) match {
      case Some(m) => {
        profile.v_add = m.group(1)
        profile.name = profile.v_add
        profile.v_ps = profile.v_add
      }
      case None =>
    }
    "\"id\":\\s*\"(.+)\"".r.findFirstMatchIn(config) match {
      case Some(m) => profile.v_id = m.group(1)
      case None =>
    }
    "\"alterId\":\\s*\"(.+)\"".r.findFirstMatchIn(config) match {
      case Some(m) => profile.v_aid = m.group(1)
      case None =>
    }
    "\"path\":\\s*\"(.+)\"".r.findFirstMatchIn(config) match {
      case Some(m) => profile.v_path = m.group(1)
      case None =>
    }
    "\"Host\":\\s*\"(.+)\"".r.findFirstMatchIn(config) match {
      case Some(m) => profile.v_host = m.group(1)
      case None =>
    }
    profile
  }
}
