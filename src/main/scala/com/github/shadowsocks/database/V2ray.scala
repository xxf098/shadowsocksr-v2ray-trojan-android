package com.github.shadowsocks.database

import java.nio.charset.Charset
import java.util
import java.util.UUID

import android.util.Base64
import com.google.gson.GsonBuilder


case class VmessQRCode(v: String,
                       ps: String,
                       add: String,
                       port: String,
                       id: String,
                       aid: String,
                       net: String,
                       `type`: String,
                       host: String,
                       path: String,
                       tls: String,
                       url_group: String
                      ) {
  override def toString: String = {
    val vmessJson = new GsonBuilder().setPrettyPrinting().create().toJson(this)
    "vmess://" + Base64.encodeToString(vmessJson.getBytes(Charset.forName("UTF-8")),
     Base64.NO_WRAP)
  }
}

case class VmessBean(
                      var guid: String,
                      var address: String,
                      var port: Int,
                      var id: String,
                      var alterId: Int,
                      var security: String,
                      var network: String,
                      var remarks: String,
                      var headerType: String,
                      var requestHost: String,
                      var path: String,
                      var streamSecurity: String,
                      var configType: Int,
                      var configVersion: Int,
                      var testResult: String,
                      var subid: String,
                      var url_group: String
                    )

object VmessBean {
  def apply(): VmessBean = {
    val guid = UUID.randomUUID().toString.replace("-", "")
    VmessBean(guid, "v2ray.com", 10086, "id", 64, "aes-128-cfb", "tcp", "def", "", "", "", "", 1, 2, "", "", "v2ray")
  }
}

case class LogBean(access: String,
                   error: String,
                   loglevel: String)

// InboundBean
case class SniffingBean(var enabled: Boolean,
                        destOverride: java.util.List[String])

case class InSettingsBean(auth: String,
                          udp: Boolean,
//                          userLevel: Int,
//                          address: String,
//                          port: Int,
                          ip: String)

case class InboundBean(tag: String,
                       var port: Int,
                       var protocol: String,
                       var listen: String,
                       var settings: InSettingsBean,
                       var sniffing: SniffingBean)

// OutboundBean
case class UsersBean(var id: String,
                     var alterId: Int,
                     var security: String,
//                     var level: Int,
                     var email: String)

case class VnextBean(var address: String,
                     var port: Int,
                     var users: util.List[UsersBean])

case class ServersBean(var address: String,
                  var method: String,
                  var ota: Boolean,
                  var password: String,
                  var port: Int,
                  var level: Int)

case class Response(var `type`: String)

case class OutSettingsBean(var vnext: util.List[VnextBean],
                      var servers: util.List[ServersBean],
                      var response: Response,
                      var domainStrategy: String)

// StreamSettingsBean
case class HeaderBean(var `type`: String,
                      var request: Any,
                      var response: Any)
object HeaderBean {
  def apply(): HeaderBean = {
    HeaderBean("None", null, null)
  }
}

case class TcpsettingsBean(var connectionReuse: Boolean = true,
                      var header: HeaderBean = HeaderBean())

case class KcpsettingsBean(var mtu: Int = 1350,
                      var tti: Int = 20,
                      var uplinkCapacity: Int = 12,
                      var downlinkCapacity: Int = 100,
                      var congestion: Boolean = false,
                      var readBufferSize: Int = 1,
                      var writeBufferSize: Int = 1,
                      var header: KcpHeaderBean = KcpHeaderBean())
case class KcpHeaderBean(var `type`: String = "None")

case class WssettingsBean(var connectionReuse: Boolean = true,
                     var path: String = "",
                     var headers: WsHeaderBean = WsHeaderBean())
case class WsHeaderBean(var Host: String = "")

case class HttpsettingsBean(var host: util.List[String] = new util.ArrayList[String](), var path: String = "")

case class TlssettingsBean(var allowInsecure: Boolean = true,
                      var serverName: String = "")

case class QuicsettingBean(var security: String = "none",
                      var key: String = "",
                      var header: QuicHeaderBean = QuicHeaderBean())
case class QuicHeaderBean(var `type`: String = "none")


// StreamSettingsBean
case class StreamSettingsBean(var network: String,
                              var security: String,
                              var tcpSettings: TcpsettingsBean,
                              var kcpsettings: KcpsettingsBean,
                              var wssettings: WssettingsBean,
                              var httpsettings: HttpsettingsBean,
                              var tlssettings: TlssettingsBean,
                              var quicsettings: QuicsettingBean
                             )

case class MuxBean(var enabled: Boolean)

case class OutboundBean(tag: String,
                        var protocol: String,
                        var settings: OutSettingsBean,
                        var streamSettings: StreamSettingsBean,
                        var mux: MuxBean)

// DnsBean
case class DnsBean(var servers: util.List[String] = null,
              var hosts: java.util.Map[String, String] = null
             )

case class RulesBean(var `type`: String = "field",
                     var ip: util.List[String] = null,
                     var domain: util.List[String] = null,
                     var outboundTag: String = "direct",
                     var port: String = null,
                     var inboundTag: util.List[String] = null)

case class RoutingBean(var domainStrategy: String,
                       var rules: util.List[RulesBean])

case class LevelBean(var handshake: Int = 4,
                     var connIdle: Int = 300,
                     var uplinkOnly: Int = 1,
                     var downlinkOnly: Int = 1)

case class PolicyBean(var levels: java.util.Map[String, LevelBean],
                 var system: Any = null)

case class V2rayConfig(stats: Any = null,
                       var log: LogBean,
                       var policy: PolicyBean,
                       var inbounds: util.List[InboundBean],
                       var outbounds: util.List[OutboundBean],
                       var dns: DnsBean,
                       var routing: RoutingBean)

object V2rayConfig {
  def apply(): V2rayConfig = {
    V2rayConfig(null, null, null, null, null, null, null)
  }
}