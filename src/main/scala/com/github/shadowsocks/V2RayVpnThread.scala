package com.github.shadowsocks

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.utils.{Parser, Route, State, TrafficMonitor, Utils}
import tun2socks.{PacketFlow, Tun2socks, Vmess, LogService => Tun2socksLogService, VpnService => Tun2socksVpnService}
import com.google.gson.{GsonBuilder, JsonParser}

import scala.language.implicitConversions
import Profile._

// TODO: connect error under some network
class V2RayVpnThread(vpnService: ShadowsocksVpnService) extends Thread {

  val TAG = "V2RayVpnService"

  @volatile var running: Boolean = false
  var pfd: ParcelFileDescriptor = _
  var inputStream: FileInputStream = _
  var outputStream: FileOutputStream = _
  var buffer = ByteBuffer.allocate(4096)

  var txTotal: Long = 0 // download
  var rxTotal: Long = 0 // upload
  val profile: Profile = vpnService.getProfile()

  class Flow(stream: FileOutputStream) extends PacketFlow {
    private val flowOutputStream = stream
    override def writePacket(pkt: Array[Byte]): Unit = {
      flowOutputStream.write(pkt)
//      rxTotal += pkt.length
      rxTotal += Tun2socks.queryStats("up")
      TrafficMonitor.update(txTotal, rxTotal)
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

  override def run(): Unit = {

    val builder = vpnService.initVPNBuilder()
    pfd = builder.establish()
    if (pfd == null ||
      !Tun2socks.setNonblock(pfd.getFd.toLong, false)) {
      Log.e(TAG, "failed to put tunFd in blocking mode")
      stopTun2Socks()
      return
    }
    inputStream = new FileInputStream(pfd.getFileDescriptor)
    outputStream = new FileOutputStream(pfd.getFileDescriptor)

    val flow = new Flow(outputStream)
    val service = new Service(vpnService)
    val androidLogService = new AndroidLogService()
    val assetPath = app.getV2rayAssetsPath()
    // replace address with ip dns
    if (profile.route == Route.CHINALIST)
      Tun2socks.setLocalDNS(s"${vpnService.china_dns_address}:${vpnService.china_dns_port}")
    else
      Tun2socks.setLocalDNS(s"${vpnService.dns_address}:${vpnService.dns_port}")
    try {
//      val config = Parser.getV2rayConfig(profile).orNull
//      Log.e(TAG, Tun2socks.checkVersion())
      profile match {
        case p if p.isVmess => {
          Tun2socks.startV2RayWithVmess(flow, service, androidLogService, profile, assetPath)
        }
        case p if p.isV2RayJSON => {
          val config = "\"address\":\\s*\".+?\"".r.replaceFirstIn(profile.v_json_config, s""""address": "${profile.v_add}"""")
          Tun2socks.startV2Ray(flow, service, androidLogService, config.getBytes(StandardCharsets.UTF_8), assetPath)
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
    vpnService.v2rayConnected()
    handlePackets()
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
          txTotal += Tun2socks.queryStats("down")
          TrafficMonitor.update(txTotal, rxTotal)
        }
      } catch {
        case e: Exception => Log.e(TAG, "failed to read bytes from TUN fd")
      }
    }
  }

  def stopTun2Socks (): Unit = {
    Tun2socks.stopV2Ray()
    if (pfd != null) pfd.close()
    pfd = null
    inputStream = null
    outputStream = null
    running = false
    vpnService.stopSelf()
    android.os.Process.killProcess(android.os.Process.myPid())
  }
}
