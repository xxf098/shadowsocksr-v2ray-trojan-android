package com.github.shadowsocks.database

import java.io.{File, IOException}
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

import android.text.TextUtils
import com.github.shadowsocks.{GuardedProcess, R}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.VmessAction.profile
import com.github.shadowsocks.utils.{ConfigUtils, ExeNative, NetUtils, TcpFastOpen, Utils}
import okhttp3.{OkHttpClient, Request}
import tun2socks.{Tun2socks, Vmess}

import scala.collection.mutable.ArrayBuffer


trait ProfileFunctions {
  var profile: Profile = _
  def getElapsed(port: Long = 8900) : Long
  def isOK(): Boolean
  def checkBypassAddr(): Unit = {
    if (List("www.google.com", "127.0.0.1", "8.8.8.8", "1.2.3.4", "1.1.1.1").contains(profile.v_add)) {
      throw new IOException(s"Bypass Host ${profile.v_add}")
    }
  }
}

object SSRAction extends ProfileFunctions {
  // TODO: refactor
  var ssTestProcess: GuardedProcess = _
  override def getElapsed(port: Long = 8900): Long = {
    var elapsed = 0L
    try {
      checkBypassAddr()
      var host = profile.host
      if (!Utils.isNumeric(host)) Utils.resolve(host, enableIPv6 = false) match {
        case Some(addr) => host = addr
        case None => throw new IOException("Host Not Resolved")
      }
      val conf = ConfigUtils
        .SHADOWSOCKS.formatLocal(Locale.ENGLISH, host, profile.remotePort, profile.localPort + 2,
        ConfigUtils.EscapedJson(profile.password), profile.method, 600, profile.protocol, profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param), ConfigUtils.EscapedJson(profile.protocol_param))
      Utils.printToFile(new File(app.getApplicationInfo.dataDir + "/ss-local-test.conf"))(p => {
        p.println(conf)
      })
      val cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL)
        , "-t", "600"
        , "-L", "www.google.com:80"
        , "-c", app.getApplicationInfo.dataDir + "/ss-local-test.conf")

      if (TcpFastOpen.sendEnabled) cmd += "--fast-open"
      ssTestProcess = new GuardedProcess(cmd).start()
      val start = currentTimeMillis
      while (start - currentTimeMillis < 3 * 1000 && NetUtils.isPortAvailable(profile.localPort + 2)) {
        try {
          Thread.sleep(100)
        } catch {
          case e: InterruptedException => Unit
        }
      }
      elapsed = NetUtils.testConnection("http://127.0.0.1:" + (profile.localPort + 2) + "/generate_204")
    } finally {
      Option(ssTestProcess).foreach(_.destroy())
    }
    elapsed
  }

  override def isOK(): Boolean = !(TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.password))
}

object VmessAction extends ProfileFunctions {
  override def getElapsed(port: Long = 8900): Long = {
    checkBypassAddr()
    val vmess: Vmess = profile
    if (!Utils.isNumeric(vmess.getAdd)) Utils.resolve(profile.v_add, enableIPv6 = false) match {
      case Some(addr) => vmess.setAdd(addr)
      case None => throw new IOException("Host Not Resolved")
    }
    Tun2socks.testVmessLatency(vmess, app.getV2rayAssetsPath(), port)
  }

  override def isOK(): Boolean = !(TextUtils.isEmpty(profile.v_add) ||
    TextUtils.isEmpty(profile.v_port) ||
    TextUtils.isEmpty(profile.v_id) ||
    TextUtils.isEmpty(profile.v_aid) ||
    TextUtils.isEmpty(profile.v_net))
}

object V2JSONAction extends ProfileFunctions {
  override def getElapsed(port: Long = 8900): Long = {
    if (TextUtils.isEmpty(profile.v_add)) throw new IOException("Server Address Not Found!")
    if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = false, hostname = "1.1.1.1") match {
      case Some(addr) => profile.v_add = addr
      case None => throw new IOException("Name Not Resolved")
    }
    val config = "\"address\":\\s*\".+?\"".r.replaceFirstIn(profile.v_json_config, s""""address": "${profile.v_add}"""")
    Tun2socks.testConfigLatency(config.getBytes(StandardCharsets.UTF_8), app.getV2rayAssetsPath())
  }

  override def isOK(): Boolean = !TextUtils.isEmpty(profile.v_json_config)
}


object ProfileConverter {

  implicit def convertProfileToAction (profile: Profile): ProfileFunctions = {
    val profileAction: ProfileFunctions = profile match {
      case p if p.isVmess => VmessAction
      case p if p.isV2RayJSON => V2JSONAction
      case p if !p.isV2Ray => SSRAction
      case _ => throw new Exception("Not Supported!")
    }
    profileAction.profile = profile
    profileAction
  }
}