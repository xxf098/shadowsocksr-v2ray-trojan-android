package com.github.shadowsocks

import java.io.{FileInputStream, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.{Parser, State, TrafficMonitor}
import tun2socks.PacketFlow
import tun2socks.Tun2socks
import tun2socks.{DBService => Tun2socksDBService, VpnService => Tun2socksVpnService}

class V2RayVpnThread(vpnService: ShadowsocksVpnService) extends Thread {

  val TAG = "V2RayVpnService"

  @volatile var running: Boolean = false
  var pfd: ParcelFileDescriptor = _
  var inputStream: FileInputStream = _
  var outputStream: FileOutputStream = _
  var buffer = ByteBuffer.allocate(1501)

  var txTotal: Long = 0
  var rxTotal: Long = 0

  class Flow(stream: FileOutputStream) extends PacketFlow {
    private val flowOutputStream = stream
    override def writePacket(pkt: Array[Byte]): Unit = {
      flowOutputStream.write(pkt)
      rxTotal += pkt.length
      TrafficMonitor.update(txTotal, rxTotal)
    }
  }

  class Service(service: VpnService) extends Tun2socksVpnService {
    private val vpnService = service
    override def protect(fd: Long): Boolean = {
      vpnService.protect(fd.toInt)
    }
  }

  class DBService() extends Tun2socksDBService {
    override def insertProxyLog(p0: String, p1: String, p2: Long, p3: Long, p4: Int, p5: Int, p6: Int, p7: Int, p8: String, p9: String, p10: Int): Unit = {
      //    Log.e(TAG, s"$p0, $p1, $p2, $p3, $p4, $p5, $p6, $p7, $p8, $p9, $p10")
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
    val dbService = new DBService()
    // check exist
    app.copyAssets("dat", vpnService.getApplicationInfo.dataDir + "/files/")
    val sniffing = "http,tls"
    val inboundTag = "tun2socks"
    Tun2socks.setLocalDNS("223.5.5.5:53")

    val config = Parser.getV2rayConfig(vpnService.getProfile()).orNull
    if (config == null) {
      return
    }
    Log.e(TAG, config)
    val ret = Tun2socks.startV2Ray(
      flow,
      service,
      dbService,
      config.getBytes(StandardCharsets.UTF_8),
      inboundTag,
      sniffing,
      vpnService.getFilesDir.getAbsolutePath
    )
    if (ret != 0) {
      Log.e(TAG, "vpn_start_err_config")
      return
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
          txTotal += n
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
  }
}
