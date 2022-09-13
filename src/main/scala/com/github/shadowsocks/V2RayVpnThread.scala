package com.github.shadowsocks

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.utils.{Key, Parser, Route, State, TrafficMonitor, Utils}
import tun2socks.{PacketFlow, QuerySpeed, Tun2socks, Vmess, LogService => Tun2socksLogService, VpnService => Tun2socksVpnService}
import com.google.gson.{GsonBuilder, JsonParser}

import scala.language.implicitConversions
import Profile._

// TODO: IPV6
class V2RayVpnThread(vpnService: ShadowsocksVpnService) extends Thread {

  val TAG = "V2RayVpnService"

  @volatile var running: Boolean = false
  var pfd: ParcelFileDescriptor = _
  var inputStream: FileInputStream = _
  var outputStream: FileOutputStream = _
  var buffer = ByteBuffer.allocateDirect(2048)

  var txTotal: Long = 0 // download
  var rxTotal: Long = 0 // upload
  val profile: Profile = vpnService.getProfile()

  class Flow(stream: FileOutputStream) extends PacketFlow {
    private val flowOutputStream = stream
    override def writePacket(pkt: Array[Byte]): Unit = {
      flowOutputStream.write(pkt)
//      rxTotal += pkt.length
//      rxTotal += Tun2socks.queryStats("up")
//      rxTotal += Tun2socks.queryOutboundStats("proxy", "downlink")
//      TrafficMonitor.update(txTotal, rxTotal)
    }
  }

  class Service(service: VpnService) extends Tun2socksVpnService {
    private val vpnService = service
    override def protect(fd: Long): Boolean = {
      vpnService.protect(fd.toInt)
    }
  }

//  class DBService() extends Tun2socksDBService {
//    override def insertProxyLog(p0: String, p1: String, p2: Long, p3: Long, p4: Int, p5: Int, p6: Int, p7: Int, p8: String, p9: String, p10: Int): Unit = {
//         Log.e(TAG, s"$p0, $p1, $p2, $p3, $p4, $p5, $p6, $p7, $p8, $p9, $p10")
//    }
//  }
  class AndroidLogService extends Tun2socksLogService {
    override def writeLog(p0: String): Unit = {
      Log.e(TAG, "===" + p0)
    }
  }

  class QuerySpeedService extends QuerySpeed {
    override def updateTraffic(p0: Long, p1: Long): Unit = {
      txTotal += p0
      rxTotal += p1
      TrafficMonitor.update(txTotal, rxTotal)
    }
  }

  override def run(): Unit = {

    val builder = vpnService.initVPNBuilder()
    pfd = builder.establish()
    if (pfd == null) {
      Log.e(TAG, "failed to put tunFd in blocking mode")
      stopTun2Socks()
      return
    }
    vpnService.v2rayConnected()

    var flow: Option[PacketFlow] = None
    val service = new Service(vpnService)
    val androidLogService = new AndroidLogService()
    val querySpeedService = new QuerySpeedService()
    val assetPath = app.getV2rayAssetsPath()
    // replace address with ip dns
    if (profile.route == Route.CHINALIST)
      Tun2socks.setLocalDNS(s"${vpnService.china_dns_address}:${vpnService.china_dns_port}")
    else
      Tun2socks.setLocalDNS(s"${vpnService.dns_address}:${vpnService.dns_port}")
    try {
//      vpnService.v2rayConnected()
      profile match {
        case p if p.isVmess => {
          app.settings.getString(Key.V2RAY_CORE, "xray") match {
            case "lite" => Tun2socks.startV2RayLiteWithTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
            case "xray" => Tun2socks.startXRayWithTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
            case _ => Tun2socks.startV2RayWithTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
          }
//          Tun2socks.startV2RayWithVmess(flow, service, androidLogService, profile, assetPath)
        }
        case p if p.isVless => {
          Log.e("===", "start startVlessTunFd")
          Tun2socks.startXVlessTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
        }
        case p if p.isShadowSocks => {
          Log.e("===", "start startShadowsocksTunFd")
//          Tun2socks.startV2RayLiteWithTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
          Tun2socks.startXShadowsocksTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
        }
        case p if p.isTrojan => {
//          Tun2socks.startTrojan(flow, service, androidLogService, profile, assetPath)
          app.settings.getString(Key.V2RAY_CORE, "xray") match {
            case "xray" => Tun2socks.startXTrojanTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
            case _ =>  Tun2socks.startTrojanTunFd(pfd.getFd.toLong, service, androidLogService, querySpeedService, profile, assetPath)
          }
        }
        case p if p.isV2RayJSON => {
          val config = if (profile.v_json_config.contains("trojan")) {
            profile.v_json_config
          } else {
            "\"address\":\\s*\".+?\"".r.replaceFirstIn(profile.v_json_config, s""""address": "${profile.v_add}"""")
          }
          inputStream = new FileInputStream(pfd.getFileDescriptor)
          outputStream = new FileOutputStream(pfd.getFileDescriptor)
          flow = Some(new Flow(outputStream))
          app.settings.getString(Key.V2RAY_CORE, "core") match {
            case "xray" => Tun2socks.startXRay(flow.get, service, androidLogService, querySpeedService, config.getBytes(StandardCharsets.UTF_8), assetPath)
            case _ => Tun2socks.startV2Ray(flow.get, service, androidLogService, querySpeedService, config.getBytes(StandardCharsets.UTF_8), assetPath)
          }
        }
        case _ => throw new Exception("Not Support!")
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        stopTun2Socks()
      }
    }
    running = true
//    vpnService.v2rayConnected()
    if (flow.isDefined) { handlePackets() }
  }

  private def handlePackets(): Unit = {
    while (running) {
      try {
        val n = inputStream.read(buffer.array())
        if (n > 0) {
          buffer.limit(n)
          Tun2socks.inputPacket(buffer.array())
          buffer.clear()
//          txTotal += n
//          txTotal += Tun2socks.queryStats("down")
//          txTotal += Tun2socks.queryOutboundStats("proxy", "uplink")
//          TrafficMonitor.update(txTotal, rxTotal)
        }
      } catch {
        case e: Exception => Log.e(TAG, "failed to read bytes from TUN fd")
      }
    }
  }

  def stopTun2Socks (stopService: Boolean = true): Unit = {
    try {
      Tun2socks.stopV2Ray()
    } catch {
      case e: Exception => Log.e(TAG, e.getMessage)
    }
    running = false
    if (pfd != null) {
      pfd.close()
      pfd = null
    }
    inputStream = null
    outputStream = null
    if (stopService) {
      vpnService.stopSelf()
      android.os.Process.killProcess(android.os.Process.myPid())
    }
  }
}
