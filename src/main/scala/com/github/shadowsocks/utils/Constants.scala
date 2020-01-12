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

object Executable {
  val REDSOCKS = "redsocks"
  val PDNSD = "pdnsd"
  val SS_LOCAL = "ss-local"
  val SS_TUNNEL = "ss-tunnel"
  val TUN2SOCKS = "tun2socks"
  val KCPTUN = "kcptun"
}

object ConfigUtils {

  def EscapedJson(OriginString:String):String = {
    val ProcessString = OriginString.replaceAll("\\\\","\\\\\\\\").replaceAll("\"","\\\\\"")

    ProcessString
  }

  val SHADOWSOCKS = "{\"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"timeout\": %d, \"protocol\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"protocol_param\": \"%s\"}"
  val REDSOCKS = "base {\n" +
    " log_debug = off;\n" +
    " log_info = off;\n" +
    " log = stderr;\n" +
    " daemon = off;\n" +
    " redirector = iptables;\n" +
    "}\n" +
    "redsocks {\n" +
    " local_ip = 127.0.0.1;\n" +
    " local_port = 8123;\n" +
    " ip = 127.0.0.1;\n" +
    " port = %d;\n" +
    " type = socks5;\n" +
    "}\n"

  val PROXYCHAINS = "strict_chain\n" +
    "localnet 127.0.0.0/255.0.0.0\n" +
    "[ProxyList]\n" +
    "%s %s %s %s %s"

  val PDNSD_LOCAL =
    """
      |global {
      | perm_cache = 2048;
      | %s
      | cache_dir = "%s";
      | server_ip = %s;
      | server_port = %d;
      | query_method = tcp_only;
      | min_ttl = 15m;
      | max_ttl = 1w;
      | timeout = 10;
      | daemon = off;
      |}
      |
      |server {
      | label = "local";
      | ip = 127.0.0.1;
      | port = %d;
      | reject = %s;
      | reject_policy = negate;
      | reject_recursively = on;
      |}
      |
      |rr {
      | name=localhost;
      | reverse=on;
      | a=127.0.0.1;
      | owner=localhost;
      | soa=localhost,root.localhost,42,86400,900,86400,86400;
      |}
    """.stripMargin

  val PDNSD_DIRECT =
    """
      |global {
      | perm_cache = 2048;
      | %s
      | cache_dir = "%s";
      | server_ip = %s;
      | server_port = %d;
      | query_method = udp_only;
      | min_ttl = 15m;
      | max_ttl = 1w;
      | timeout = 10;
      | daemon = off;
      | par_queries = 4;
      |}
      |
      |%s
      |
      |server {
      | label = "local-server";
      | ip = 127.0.0.1;
      | query_method = tcp_only;
      | port = %d;
      | reject = %s;
      | reject_policy = negate;
      | reject_recursively = on;
      |}
      |
      |rr {
      | name=localhost;
      | reverse=on;
      | a=127.0.0.1;
      | owner=localhost;
      | soa=localhost,root.localhost,42,86400,900,86400,86400;
      |}
    """.stripMargin

    val REMOTE_SERVER =
      """
        |server {
        | label = "remote-servers";
        | ip = %s;
        | port = %d;
        | timeout = 3;
        | query_method = udp_only;
        | %s
        | policy = included;
        | reject = %s;
        | reject_policy = fail;
        | reject_recursively = on;
        |}
      """.stripMargin

  val V2RAY_CONFIG =
    """{
      |    "log": {
      |        "access": "",
      |        "error": "",
      |        "loglevel": "error"
      |    },
      |    "dns": {
      |        "servers": [
      |            "1.0.0.1",
      |            "localhost"
      |        ]
      |    },
      |    "routing": {
      |        "domainStrategy": "IPIfNonMatch",
      |        "rules": [
      |            {
      |                "type": "field",
      |                "ip": [
      |                    "geoip:private",
      |                    "geoip:cn"
      |                ],
      |                "outboundTag": "direct"
      |            },
      |            {
      |                "type": "field",
      |                "domain": [
      |                    "geosite:cn"
      |                ],
      |                "outboundTag": "direct"
      |            }
      |        ]
      |    },
      |    "inbounds": [
      |        {
      |            "tag": "socks-in",
      |            "port": 8088,
      |            "listen": "::",
      |            "protocol": "socks",
      |            "settings": {
      |                "auth": "noauth",
      |                "udp": true,
      |                "ip": "127.0.0.1"
      |            }
      |        },
      |        {
      |            "tag": "http-in",
      |            "port": 8090,
      |            "listen": "::",
      |            "protocol": "http"
      |        }
      |    ],
      |    "outbounds": [
      |        {
      |            "protocol": "vmess",
      |            "description": "vmess description",
      |            "settings": {
      |                "vnext": [
      |                    {
      |                        "address": "127.0.0.1",
      |                        "port": 4080,
      |                        "users": [
      |                            {
      |                                "email": "email@gmail.com",
      |                                "id": "abc-123-def-456-ghi",
      |                                "alterId": 2,
      |                                "security": "auto"
      |                            }
      |                        ]
      |                    }
      |                ]
      |            },
      |            "mux": {
      |                "enabled": true
      |            },
      |            "tag": "proxy",
      |            "streamSettings": {
      |                "network": "ws",
      |                "wsSettings": {
      |                    "connectionReuse": true,
      |                    "path": "/v2ray",
      |                    "headers": {
      |                        "Host": "microsoft.com"
      |                    }
      |                }
      |            }
      |        },
      |        {
      |            "protocol": "freedom",
      |            "tag": "direct",
      |            "settings": {
      |                "domainStrategy": "UseIP"
      |            }
      |        }
      |    ]
      |}""".stripMargin
}

object Key {
  val id = "profileId"
  val name = "profileName"

  val individual = "Proxyed"

  val isNAT = "isNAT"
  val route = "route"
  val aclurl = "aclurl"

  val isAutoConnect = "isAutoConnect"

  val proxyApps = "isProxyApps"
  val bypass = "isBypassApps"
  val udpdns = "isUdpDns"
  val auth = "isAuth"
  val ipv6 = "isIpv6"

  val host = "proxy"
  val password = "sitekey"
  val method = "encMethod"
  val remotePort = "remotePortNum"
  val localPort = "localPortNum"

  // v2ray
  val v_host = "v_host"
  val v_path = "v_path"
  val v_tls = "v_tls"
  val v_verify_cert = "v_verify_cert"
  val v_add = "v_add"
  val v_port = "v_port"
  val v_aid = "v_aid"
  val v_net = "v_net"
  val v_type = "v_type"
  val v_v = "v_v"
  val v_ps = "v_ps"
  val v_id = "v_id"
  val v_class = "v_class"

  val profileTip = "profileTip"

  val obfs = "obfs"
  val obfs_param = "obfs_param"
  val protocol = "protocol"
  val protocol_param = "protocol_param"
  val dns = "dns"
  val china_dns = "china_dns"

  val tfo = "tcp_fastopen"
  val currentVersionCode = "currentVersionCode"
  val logcat = "logcat"
  val frontproxy = "frontproxy"
  val ssrsub_autoupdate = "ssrsub_autoupdate"
  val group_name = "groupName"
  val currentGroupName = "currentGroupName"

  val EXTRA_PROFILE_ID = "EXTRA_PROFILE_ID"
}

object State {
  val CONNECTING = 1
  val CONNECTED = 2
  val STOPPING = 3
  val STOPPED = 4
  def isAvailable(state: Int): Boolean = state != CONNECTED && state != CONNECTING
}

object Action {
  val SERVICE = "in.zhaoj.shadowsocksrr.SERVICE"
  val CLOSE = "in.zhaoj.shadowsocksrr.CLOSE"
  val QUICK_SWITCH = "in.zhaoj.shadowsocksrr.QUICK_SWITCH"
  val SCAN = "in.zhaoj.shadowsocksrr.intent.action.SCAN"
  val SORT = "in.zhaoj.shadowsocksrr.intent.action.SORT"
}

object Route {
  val ALL = "all"
  val BYPASS_LAN = "bypass-lan"
  val BYPASS_CHN = "bypass-china"
  val BYPASS_LAN_CHN = "bypass-lan-china"
  val GFWLIST = "gfwlist"
  val CHINALIST = "china-list"
  val ACL = "self"
  val BLOCK_DOMAIN = List("baidu.com",
    "www.auspiciousvp.com",
    "auspiciousvp.com",
    "baidustatic.com",
    "umeng.com",
    "umengcloud.com",
    "uc.cn",
    "uc.com",
    "snssdk.com",
    "ixigua.com",
    "byteimg.com",
    "doubleclick.net",
    "sogou.com")
}

object EConfigType {
  val Vmess = 1
  val Custom = 2
  val Shadowsocks = 3
  val Socks = 4
}
