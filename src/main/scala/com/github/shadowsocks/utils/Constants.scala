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

object ExeNative {
  val REDSOCKS = "libredsocks.so"
  val PDNSD = "libpdnsd.so"
  val SS_LOCAL = "libss-local.so"
  val SHADOWSOCKS_LOCAL = "libsslocal.so"
//  val SS_TUNNEL = "ss-tunnel"
  val TUN2SOCKS = "libtun2socks.so"
  val PROXYCHAINS = "libproxychains4.so"
//  val KCPTUN = "kcptun"

  def getLocalBin(isShadowSocks: Boolean): String = {
    if (isShadowSocks) SHADOWSOCKS_LOCAL else SS_LOCAL
  }
}

object ConfigUtils {

  def EscapedJson(OriginString:String):String = {
    val ProcessString = OriginString.replaceAll("\\\\","\\\\\\\\").replaceAll("\"","\\\\\"")

    ProcessString
  }

  val SHADOWSOCKS = "{\"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"timeout\": %d, \"protocol\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"protocol_param\": \"%s\"}"
  val SHADOWSOCKS_LOCAL = "{\"server\": \"%s\", \"server_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"dns\":\"udp://223.5.5.5:53\", \"locals\":[{\"local_address\":\"127.0.0.1\",\"local_port\":1080,\"local_udp_address\":\"127.0.0.1\",\"local_udp_port\":1080,\"mode\":\"tcp_and_udp\"}, {\"local_address\":\"127.0.0.1\",\"local_port\":1143,\"local_udp_address\":\"127.0.0.1\",\"local_udp_port\":1143,\"mode\":\"tcp_and_udp\"}]}"
  val SHADOWSOCKSR_TEST_SERVER = "{\"id\": \"%s\", \"server\": \"%s\", \"server_port\": %d, \"local_port\": %d, \"password\": \"%s\", \"method\":\"%s\", \"protocol\": \"%s\", \"protocol_param\": \"%s\", \"obfs\": \"%s\", \"obfs_param\": \"%s\", \"group\": \"ssr\", \"enable\": true}"
  val SHADOWSOCKSR_TEST_CONF = "{\"conf_ver\": 2, \"servers\": [%s], \"timeout\": %d, \"nameserver\":\"223.5.5.5\", \"tunnel_address\": \"%s\", \"ipv6_first\": false}"
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
      | %s
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
      | perm_cache = 4096;
      | %s
      | cache_dir = "%s";
      | server_ip = %s;
      | server_port = %d;
      | query_method = udp_only;
      | min_ttl = 15m;
      | max_ttl = 5d;
      | timeout = 10;
      | daemon = off;
      | par_queries = 4;
      | %s
      | debug = off;
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

  val REMOTE_SERVER1 =
    """
      |server {
      | label = "remote-servers1";
      | ip = 223.5.5.5,1.1.1.1;
      | timeout = 5;
      | proxy_only=on;
      | lean_query=on;
      | query_method = udp_only;
      | policy = included;
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
      |            "1.1.1.1",
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
  val isPerAppProxyEnabled = "isPerAppProxyEnabled"
  val isPerAppProxyBypassMode = "isPerAppProxyBypassMode"
  val perAppProxy = "perAppProxy"

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
  val v_add_json = "v_add_json"
  val v_id_json = "v_id_json"
  val v_aid_json = "v_aid_json"
  val v_security_json = "v_security_json"
  val v_security = "v_security"
  val v_headertypes = "v_headertypes"
  val v_allowInsecure = "v_allowInsecure"

  //trojan
  val t_sni = "t_sni"
  val t_addr = "t_addr"
  val t_verify_certificate = "t_verify_certificate"

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

  val FRAGMENT_NAME = "FRAGMENT_NAME"
  val FRAGMENT_V2RAY_CONFIG = "FRAGMENT_V2RAY_CONFIG"
  val FRAGMENT_SUBSCRIPTION = "FRAGMENT_SUBSCRIPTION"
  val FRAGMENT_ROUTE_RULE = "FRAGMENT_ROUTE_RULE"
  val FRAGMENT_NEW_PROFILE = "FRAGMENT_NEW_PROFILE"
  val SUBSCRIPTION_GROUP_NAME = "SUBSCRIPTION_GROUP_NAME"
  val SUBSCRIPTION_UPDATED = "SUBSCRIPTION_UPDATED"

  // settings
  val SORT_METHOD = "pref_sort_method"
  val SORT_METHOD_DEFAULT = "default"
  val SORT_METHOD_ELAPSED = "elapsed"
  val SORT_METHOD_DOWNLOAD = "download"
  val GROUP_SORT_METHOD = "group_sort_method"
  val GROUP_SORT_METHOD_DEFAULT = "default"
  val GROUP_SORT_METHOD_REVERSE = "reverse"
  val HIDE_SERVER = "pref_hide_server"
  val SELECT_DISPLAY_INFO = "pref_select_display_info"
  val PING_METHOD = "pref_ping_method"
  val AUTO_UPDATE_SUBSCRIPTION = "pref_auto_update_subscription"
  val AUTO_TEST_CONNECTIVITY = "pref_auto_test_connectivity"
  val SSR_DNS_NOCAHCE = "pref_ssr_dns_nocache1" // wrong name
  val FULL_TEST_BG = "pref_full_test_bg"
  val ENABLE_SNIFF_DOMAIN = "pref_enable_sniff_domain"
  val ENABLE_LOCAL_HTTP_PROXY = "enable_local_http_proxy"
  val TEST_ZERO_MS = "pref_only_test_zero_ms"
  val LOG_LEVEL = "pref_log_level"
  val MUX = "pref_mux_cool"
  val V2RAY_CORE = "pref_v2ray_core"
  val TEST_CONCURRENCY = "pref_test_concurrency"

  // message
  val TEST_PROFILE_IDS = "test_profile_ids"

  // aead
  val AES_128_GCM = "aes-128-gcm"
  val AES_192_GCM = "aes-192-gcm"
  val AES_256_GCM = "aes-256-gcm" // use xray
  val CHACHA20_IETF_POLY1305 = "chacha20-ietf-poly1305"
  val XCHACHA20_IETF_POLY1305 = "xchacha20-ietf-poly1305"
}

object State {
  val CONNECTING = 1
  val CONNECTED = 2
  val STOPPING = 3
  val STOPPED = 4
  def isAvailable(state: Int): Boolean = state != CONNECTED && state != CONNECTING
}

object Action {
  val SERVICE = "com.xxf098.ssrray.SERVICE"
  val CLOSE = "com.xxf098.ssrray.CLOSE"
  val QUICK_SWITCH = "com.xxf098.ssrray.QUICK_SWITCH"
  val SCAN = "com.xxf098.ssrray.intent.action.SCAN"
  val SORT = "com.xxf098.ssrray.intent.action.SORT"
  val SORT_DOWNLOAD = "com.xxf098.ssrray.intent.action.SORT_DOWNLOAD"
  val STOP_TEST = "com.xxf098.ssrray.STOP_TEST"
}

object Route {
  val ALL = "all"
  val BYPASS_LAN = "bypass-lan"
  val BYPASS_CHN = "bypass-china"
  val BYPASS_LAN_CHN = "bypass-lan-china"
  val GFWLIST = "gfwlist"
  val CHINALIST = "china-list"
  val ACL4SSR_BANDAD = "banAD"
  val ACL4SSR_GFWLIST_BANAD = "gfwlist-banAD"
  val ACL4SSR_ONLYBANAD = "onlybanAD"
  val ACL4SSR_FULLGFWLIST = "fullgfwlist"
  val ACL4SSR_BACKCN_BANAD = "backcn-banAD"
  val ACL4SSR_NOBANAD = "nobanAD"
  val ACL = "self"
  val DEFAULT_ROUTES = List(BYPASS_CHN, BYPASS_LAN, BYPASS_LAN_CHN, CHINALIST, GFWLIST)
  val ACL4SSR_ROUTES = List(ACL4SSR_BANDAD, ACL4SSR_GFWLIST_BANAD, ACL4SSR_ONLYBANAD, ACL4SSR_FULLGFWLIST, ACL4SSR_BACKCN_BANAD, ACL4SSR_NOBANAD)
  val ALL_ROUTES = List(
    ALL,
    BYPASS_LAN, BYPASS_CHN, BYPASS_LAN_CHN, GFWLIST, CHINALIST,
    ACL4SSR_BANDAD, ACL4SSR_GFWLIST_BANAD, ACL4SSR_ONLYBANAD, ACL4SSR_FULLGFWLIST, ACL4SSR_BACKCN_BANAD, ACL4SSR_NOBANAD,
    ACL
  )
  val BLOCK_DOMAIN = List(
//    "baidu.com",
//    "www.auspiciousvp.com",
    "auspiciousvp.com",
//    "baidustatic.com",
//    "umeng.com",
//    "umengcloud.com",
//    "uc.cn",
//    "uc.com",
//    "snssdk.com",
//    "ixigua.com",
//    "byteimg.com",
    "doubleclick.net"
  )
}

object EConfigType {
  val Vmess = 1
  val Custom = 2
  val Shadowsocks = 3
  val Socks = 4
}
