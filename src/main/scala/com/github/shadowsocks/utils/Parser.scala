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
import android.net.Uri
import tun2socks.Tun2socks

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

object Parser {
  val TAG = "ShadowParser"
  private val pattern = "(?i)ss://([A-Za-z0-9+-/=_]+)(#(.+))?".r
  private val decodedPattern = "(?i)^((.+?)(-auth)??:(.*)@(.+?):(\\d+?))$".r

  private val pattern_ssr = "(?i)ssr://([-A-Za-z0-9_/=]+)".r
  private val decodedPattern_ssr = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):([^/]+))".r
  private val decodedPattern_ssr_obfsparam = "(?i)[?&]obfsparam=([A-Za-z0-9%_/=-]*)".r
  private val decodedPattern_ssr_remarks = "(?i)[?&]remarks=([A-Za-z0-9%_/+=-]*)".r
  private val decodedPattern_ssr_protocolparam = "(?i)[?&]protoparam=([A-Za-z0-9_/=-]*)".r
  private val decodedPattern_ssr_groupparam = "(?i)[?&]group=([A-Za-z0-9_/=-]*)".r

  private val pattern_vmess = "(?i)(vmess://[-A-Za-z0-9_/+=]+([?#]\\S+)?)".r
  private val pattern_trojan = "(?i)(trojan://(\\S+?)@(\\S+?):(\\d{2,5})/?([\\?#].*)?)".r
  private val pattern_vless = "(?i)(vless://(\\S+?)@(\\S+?):(\\d{2,5})([\\?#].*)?)".r
  private val pattern_trojan_query = "(?i)allowInsecure=([01])&(peer|sni)=(.+?)#(.+)?".r
  private val pattern_shadwosocks = "(?i)(ss://(\\S+?)@(\\S+?):(\\d{2,5})/?([\\?#].*)?)".r
  private val pattern_shadwosocks1 = "(?i)(ss://([-A-Za-z0-9_/+=]+)([\\?#].*)?)".r


  def decodeBase64 (data: String): String = {
    val resp = data.replaceAll("=", "")
      .replaceAll("\\+", "-")
      .replaceAll("/", "_")
    val decodeData = Try(Base64.decode(resp, Base64.URL_SAFE | Base64.NO_PADDING)) match {
      case Failure(exception) => "".getBytes
      case Success(value) => value
    }
    new String(decodeData, "UTF-8")
  }

  def findAll(data: CharSequence) = pattern.findAllMatchIn(if (data == null) "" else data).map(m => try
    decodedPattern.findFirstMatchIn(new String(Base64.decode(m.group(1), Base64.NO_PADDING), "UTF-8")) match {
      case Some(ss)=>
        if (!data.toString.startsWith("vmess://") && !data.toString.startsWith("vless://")) {
          val profile = new Profile
          profile.method = ss.group(2).toLowerCase
          if (ss.group(3) != null) profile.protocol = "verify_sha1"
          profile.password = ss.group(4)
          profile.name = ss.group(5)
          profile.host = profile.name
          profile.remotePort = ss.group(6).toInt
          if (m.group(2) != null) profile.name = URLDecoder.decode(m.group(3), "utf-8")
          if (m.group(0) != null && m.group(0).startsWith("ss://")) {
            profile.proxy_protocol = "shadowsocks"
            val de = new String(Base64.decode(m.group(1), Base64.NO_PADDING), "UTF-8")
            val link = Uri.parse(s"ss://${de}")
            profile.url_group = "ss"
            profile.host = link.getHost
            profile.v_add = profile.host
            profile.remotePort = link.getPort
            profile.v_port = s"${profile.remotePort}"
            val userInfo = link.getUserInfo.split(":", 2)
            if (userInfo.length > 1) {
              profile.method = userInfo(0)
              profile.password = userInfo(1)
              profile.v_security = profile.method
              profile.v_id = profile.password
            }
          }
          profile
        } else {
          null
        }
      case _ => null
    }
    catch {
      case ex: Exception =>
        Log.e(TAG, "parser error: " + m.source, ex)// Ignore
        null
    }).filter(_ != null)

  def findAll_ssr(data: CharSequence) = pattern_ssr.findAllMatchIn(if (data == null) "" else data).map(m => try{
    val uri = decodeBase64(m.group(1).replaceAll("=", ""))
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
                profile.obfs_param = new String(Base64.decode(Uri.decode(param.group(1)).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            decodedPattern_ssr_protocolparam.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.protocol_param = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            decodedPattern_ssr_remarks.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.name = decodeBase64(Uri.decode(param.group(1)).replaceAll("=", ""))
              case _ => profile.name = ss.group(2).toLowerCase
            }

            decodedPattern_ssr_groupparam.findFirstMatchIn(uri) match {
              case Some(param) =>
                profile.url_group = new String(Base64.decode(param.group(1).replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
              case _ => null
            }

            profile.proxy_protocol = "ssr"

            profile
          case _ => null
        }
    }
    catch {
      case ex: Exception =>
        Log.e(TAG, "parser ssr error: " + m.source, ex)// Ignore
        null
    }).filter(_ != null)

  def findAllVmess(data: CharSequence) = pattern_vmess
    .findAllMatchIn(if (data == null) "" else data)
    .flatMap(m => try {
      val g = m.group(1)
      val group = if (g.indexOf("?") > 0) {
        val splits = g.split("\\?", 2)
        s"${splits(0).replaceAll("=", "")}?${splits(1)}"
      } else { g.replaceAll("=", "") }
      findVmess(group)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "parser vmess error: " + m.source, ex) // Ignore
        None
    })
    .map(convertVmessBeanToProfile)

  // TODO: v_host t_peer
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
    profile.t_allowInsecure = vmessBean.allowInsecure
    // common
    profile.name = profile.v_ps
    profile.url_group = vmessBean.url_group
    profile.route = Route.BYPASS_LAN_CHN
    profile.localPort = 10809
    profile
  }

  def findAllVless(data: CharSequence) = pattern_vless
    .findAllMatchIn(if (data == null) "" else data)
    .flatMap(m => try {
      val vlessUri = Uri.parse(m.group(1))
      if (vlessUri.getScheme == "vless") {
        val profile = new Profile
        val host = vlessUri.getHost
        val port = vlessUri.getPort
        val password = vlessUri.getUserInfo
        profile.t_password = password
        profile.t_addr = host
        profile.t_port = port
        // vmess
        profile.v_id = password
        profile.v_add = host
        profile.v_port = s"$port"
        profile.proxy_protocol = "vless"
        profile.name = host
        profile.v_ps = host
        profile.v_aid = "0"
        // security tls
        val security = vlessUri.getQueryParameter("security")
        if (!TextUtils.isEmpty(security)) {
          profile.v_tls = security;
        }
        // sni
        val sni = vlessUri.getQueryParameter("sni")
        if (!TextUtils.isEmpty(sni)) {
          profile.t_peer = sni;
        }
        // encryption
        val encryption = vlessUri.getQueryParameter("encryption")
        if (!TextUtils.isEmpty(encryption)) {
          profile.v_encryption = encryption;
        }
        // headerType
        val headerType = vlessUri.getQueryParameter("headerType")
        if (!TextUtils.isEmpty(headerType)) {
          profile.v_type = headerType;
        }
        // type: tcp, http, ws
        val networkType = vlessUri.getQueryParameter("type")
        if (!TextUtils.isEmpty(networkType)) {
          profile.v_net = networkType;
        }
        // path
        val path = vlessUri.getQueryParameter("path")
        if (!TextUtils.isEmpty(path)) {
          profile.v_path = Uri.decode(path)
        }
        // ws host
        val wsHost = vlessUri.getQueryParameter("host")
        if (!TextUtils.isEmpty(wsHost)) {
          profile.v_host = wsHost
        }
        val splits = m.group(1).split("#")
        if (splits.length > 1) {
          profile.name = URLDecoder.decode(splits.last, "UTF-8")
          profile.v_ps = profile.name
        }
        // setup common
        profile.url_group = "vless"
        profile.host = host
        profile.remotePort = port
        profile.localPort = 10809
        profile.route = Route.BYPASS_LAN_CHN
        profile.password = password
        profile.t_allowInsecure = true
        Some(profile)
      } else {
        None
      }
    }catch {
      case ex: Exception =>None
    })

  def findAllTrojan(data: CharSequence) = pattern_trojan
    .findAllMatchIn(if (data == null) "" else data)
    .flatMap(m => try {
//      Log.e(TAG, m.group(1))
      val trojanUri = Uri.parse(m.group(1))
      if (trojanUri.getScheme == "trojan") {
        val profile = new Profile
        val host = trojanUri.getHost
        val port = trojanUri.getPort
        val password = trojanUri.getUserInfo
        profile.t_password = password
        profile.t_addr = host
        profile.t_port = port
        profile.t_peer = host
        profile.proxy_protocol = "trojan"
        // common
        profile.url_group = "trojan"
        profile.host = host
        profile.v_host = host
        profile.remotePort = port
        profile.localPort = 10809
        profile.name = host
        profile.route = Route.BYPASS_LAN_CHN
        profile.password = password
        profile.t_allowInsecure = false
        // sni
        var sni = trojanUri.getQueryParameter("sni")
        sni = if (TextUtils.isEmpty(sni)) { trojanUri.getQueryParameter("peer") } else sni
        if (!TextUtils.isEmpty(sni) && sni != profile.t_peer) {
          profile.t_peer = sni
          profile.v_host = sni
          profile.t_allowInsecure = true
        }
        // allowInsecure
        if (trojanUri.getQueryParameter("allowInsecure") == "1") {
          profile.t_allowInsecure = true
        }
        // security
        if (trojanUri.getQueryParameter("tls") == "tls") {
          profile.t_allowInsecure = false
        }
        // type=ws
        val networkType = trojanUri.getQueryParameter("type")
        if (!TextUtils.isEmpty(networkType)) { profile.v_net = networkType }
        // path
        val path = trojanUri.getQueryParameter("path")
        if (!TextUtils.isEmpty(path)) { profile.v_path = Uri.decode(path) }
        // ws host
        val wsHost = trojanUri.getQueryParameter("host")
        if (!TextUtils.isEmpty(wsHost)) { profile.v_host = wsHost }
        // get profile name
        val splits = m.group(1).split("#")
        if (splits.length > 1) {
          profile.name = URLDecoder.decode(splits.last, "UTF-8")
        }
//        Log.e(TAG, profile.toString())
        Some(profile)
      } else {
        None
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "parser trojan error: " + m.source, ex) // Ignore
        None
    })

  def findAllShadowSocks(data: CharSequence) = (pattern_shadwosocks.findAllMatchIn(if (data == null) "" else data) ++
      pattern_shadwosocks1.findAllMatchIn(if (data == null) "" else data) )
    .flatMap(m => try {
//      Log.e(TAG, m.group(1))
      var shadowsocksUri = Uri.parse(m.group(1))
      if (shadowsocksUri.getScheme == "ss" && !shadowsocksUri.getHost.contains(".")) {
        try {
          val info = new String(Base64.decode(shadowsocksUri.getHost.replaceAll("=", ""), Base64.URL_SAFE), "UTF-8")
          val url = s"ss://${info}#${shadowsocksUri.getEncodedFragment}"
          shadowsocksUri = Uri.parse(url)
        } catch {
          case ex: Exception => { }
        }
      }
      if (shadowsocksUri.getScheme == "ss" && shadowsocksUri.getUserInfo != null && !Option(shadowsocksUri.getQuery).exists(s => s.contains("headerType="))) {
        val profile = new Profile
        val host = shadowsocksUri.getHost
        val port = shadowsocksUri.getPort
        val uri = if (shadowsocksUri.getUserInfo.contains(":")) { shadowsocksUri.getUserInfo }
            else { new String(Base64.decode(shadowsocksUri.getUserInfo.replaceAll("=", ""), Base64.URL_SAFE), "UTF-8") }
        val passwordMethod = uri.split(":")
        if (passwordMethod.size < 2 || port < 0) {
          None
        } else {
          val password = passwordMethod(1)
          profile.v_add = host
          profile.v_port = port.toString
          profile.v_id = password
          profile.v_security = passwordMethod(0)
          profile.proxy_protocol = "shadowsocks"
          // common
          profile.url_group = "ss"
          profile.host = host
          profile.remotePort = port
          profile.localPort = 1080
          profile.name = host
          profile.route = Route.BYPASS_LAN_CHN
          profile.password = password
          profile.method = passwordMethod(0)
          // get profile name
          val splits = m.group(1).split("#")
          if (splits.length > 1) {
            profile.name = URLDecoder.decode(splits.last, "UTF-8")
          }
          Log.e(TAG, s"${profile.host}:${profile.remotePort} ${profile.method}:${profile.password}")
          Some(profile)
        }
      } else {
        None
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "parser ss error: " + m.source, ex) // Ignore
        None
    })

  def findAllClash(clashData: String) = {
    val data = clashData.split("\n").takeWhile(s => !s.contains("proxy-groups:")).mkString("\n")
    Try(Tun2socks.parseClash(data)).toOption.flatMap(s => if (s.length> 0) Some(s) else None)
  }

  def peekClash(clashData: String) = {
    val data = clashData.split("\n").takeWhile(s => !s.contains("proxy-groups:")).mkString("\n")
    Try(Tun2socks.peekClash(data, 1)).toOption.flatMap(s => if (s.length> 0) Some(s) else None)
  }

  // single link findVless
  def findVmess (vmessLink: String): Option[VmessBean] = {
    if (vmessLink == null ||
      TextUtils.isEmpty(vmessLink) ||
    !vmessLink.startsWith("vmess://")) return None
    val indexSplit = vmessLink.indexOf("?")
    if (indexSplit > 0) return findShadowrocketVmess(vmessLink)
    var result = vmessLink.replace("vmess://", "")
        .replace("+", "-")
        .replace("/", "_")
    result = new String(Base64.decode(result, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8")
//    Log.e(TAG, result)
    val vmessQRCode = new Gson().fromJson(result, classOf[VmessQRCode])
    vmessQRCode2VmessBean(vmessQRCode)
  }

  def findShadowrocketVmess (link: String): Option[VmessBean] = {
    val url = Uri.parse(link)

    val vmess = VmessBean()
    vmess.configType = EConfigType.Vmess
    vmess.security = "auto"
    vmess.network = "tcp"
    vmess.configVersion = 2

    val b64 = url.getHost
    val s = new String(Base64.decode(b64, Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8")
    val mhp = s.split(":", 3)
    if (mhp.length != 3) {
      return None
    }
    vmess.security = mhp(0)
    vmess.port = mhp(2).toInt
    val idAddr = mhp(1).split("@", 2)
    if (idAddr.length != 2 ) {
      return None
    }
    vmess.id = idAddr(0)
    vmess.address = idAddr(1)
    vmess.alterId = 0

    vmess.remarks = Option(url.getQueryParameter("remarks")).getOrElse("")
    vmess.path = Option(url.getQueryParameter("path")).getOrElse("")
    vmess.streamSecurity = if (Option(url.getQueryParameter("tls")).getOrElse("") == "1") "tls" else ""
    vmess.alterId = Option(url.getQueryParameter("alterId")).getOrElse("0").toInt
    val obfs = Option(url.getQueryParameter("obfs")).getOrElse("none")
    if (obfs == "websocket") { vmess.network = "ws" }
    else if (obfs == "none" ) {
      vmess.network = "tcp"
      vmess.headerType = "none"
    }
    vmess.requestHost =Option(url.getQueryParameter("obfsParam")).getOrElse("")
    Some(vmess)
  }

  def checkCipher(cipher: String): Option[String] = {
    if (cipher == "auto" || cipher == "none" || cipher == "aes-128-gcm" || cipher == "chacha20-poly1305") { Some(cipher) } else { None }
  }

  def vmessQRCode2VmessBean (vmessQRCode: VmessQRCode): Option[VmessBean] = {
    if (TextUtils.isEmpty(vmessQRCode.add) ||
        TextUtils.isEmpty(vmessQRCode.port) ||
        TextUtils.isEmpty(vmessQRCode.id) ||
        TextUtils.isEmpty(vmessQRCode.aid)) return None

    val vmess = VmessBean()
    vmess.configType = EConfigType.Vmess
//    vmess.security = "auto"
//    vmess.network = "tcp"

    vmess.configVersion = Option(vmessQRCode.v).getOrElse("2").toInt
    vmess.remarks = Option(vmessQRCode.ps).getOrElse(vmessQRCode.add)
    vmess.address = vmessQRCode.add
    vmess.port = vmessQRCode.port.toInt
    vmess.id = vmessQRCode.id
    vmess.alterId = if (vmessQRCode.aid.length > 0) {vmessQRCode.aid.toInt} else {0}
    vmess.network = Option(vmessQRCode.net).getOrElse("tcp")
    vmess.headerType = Option(vmessQRCode.`type`).getOrElse("none")
    vmess.requestHost = Option(vmessQRCode.host).getOrElse("")
    vmess.path = Option(vmessQRCode.path).getOrElse("")
    vmess.streamSecurity = Option(vmessQRCode.tls).getOrElse("")
    vmess.subid = ""
    vmess.security = checkCipher(vmessQRCode.security).getOrElse(checkCipher(vmessQRCode.scy).getOrElse("auto"))
    vmess.url_group = if (TextUtils.isEmpty(vmessQRCode.url_group)) vmess.url_group else vmessQRCode.url_group
    vmess.allowInsecure = vmessQRCode.skipCertVerify
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
