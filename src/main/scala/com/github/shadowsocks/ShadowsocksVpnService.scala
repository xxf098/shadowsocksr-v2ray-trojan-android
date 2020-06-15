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

package com.github.shadowsocks

import java.io.{File, FileDescriptor, FileInputStream, FileOutputStream, IOException, PrintWriter}
import java.util.Locale

import scala.io.Source
import java.net._
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import android.annotation.SuppressLint
import android.content._
import android.content.pm.PackageManager.NameNotFoundException
import android.net.{ConnectivityManager, LocalSocket, LocalSocketAddress, Network, NetworkCapabilities, NetworkInfo, NetworkRequest, VpnService}
import android.os._
import android.system.Os
import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.job.AclSyncJob
import com.github.shadowsocks.utils._
import com.github.shadowsocks.utils.CloseUtils._
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network

import scala.collection.mutable.ArrayBuffer


class ShadowsocksVpnService extends VpnService with BaseService {
  val TAG = "ShadowsocksVpnService"
  val VPN_MTU = 1500
  val PRIVATE_VLAN = "26.26.26.%s"
  val PRIVATE_VLAN6 = "fdfe:dcba:9876::%s"
  var conn: ParcelFileDescriptor = _
  var vpnThread: ShadowsocksVpnThread = _
  private var notification: ShadowsocksNotification = _

  var sslocalProcess: GuardedProcess = _
  var sstunnelProcess: GuardedProcess = _
  var pdnsdProcess: GuardedProcess = _
  var tun2socksProcess: GuardedProcess = _
  var proxychains_enable: Boolean = false
  var host_arg = ""
  var dns_address = ""
  var dns_port = 53
  var china_dns_address = ""
  var china_dns_port = 53

  var v2rayThread: V2RayVpnThread = _

  override def onBind(intent: Intent): IBinder = {
    val action = intent.getAction
    if (VpnService.SERVICE_INTERFACE == action) {
      return super.onBind(intent)
    } else if (Action.SERVICE == action) {
      return binder
    }
    null
  }

  override def onRevoke() {
    stopRunner(true)
  }

  override def stopRunner(stopService: Boolean, msg: String = null) {

    if (vpnThread != null) {
      vpnThread.stopThread()
      vpnThread = null
    }

    if (notification != null) notification.destroy()

    // channge the state
    changeState(State.STOPPING)

    app.track(TAG, "stop")

    // reset VPN
    killProcesses()

    // close connections
    if (conn != null) {
      conn.close()
      conn = null
    }

    Option(profile).filter(_.isV2Ray).flatMap(_ => Option(v2rayThread))
      .foreach(_ => v2rayThread.stopTun2Socks())

    super.stopRunner(stopService, msg)
  }

  def killProcesses() {
    if (sslocalProcess != null) {
      sslocalProcess.destroy()
      sslocalProcess = null
    }
    if (sstunnelProcess != null) {
      sstunnelProcess.destroy()
      sstunnelProcess = null
    }
    if (tun2socksProcess != null) {
      tun2socksProcess.destroy()
      tun2socksProcess = null
    }
    if (pdnsdProcess != null) {
      pdnsdProcess.destroy()
      pdnsdProcess = null
    }
  }

  override def startRunner(profile: Profile) {

    // ensure the VPNService is prepared
    if (VpnService.prepare(this) != null) {
      val i = new Intent(this, classOf[ShadowsocksRunnerActivity])
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(i)
      stopRunner(true)
      return
    }

    super.startRunner(profile)
  }

  def v2rayConnected(): Unit = {
    changeState(State.CONNECTED)
    notification = new ShadowsocksNotification(this, profile.name, "service-v2ray")
  }

  override def connect() : Any = {
    super.connect()

    if (new File(getApplicationInfo.dataDir + "/proxychains.conf").exists) {
      proxychains_enable = true
      //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo.dataDir + "/proxychains.conf", true)
      //Os.setenv("PROXYCHAINS_PROTECT_FD_PREFIX", getApplicationInfo.dataDir, true)
    } else {
      proxychains_enable = false
    }

    val dnsConf = profile.getDNSConf()
    dns_address = dnsConf._1
    dns_port = dnsConf._2
    china_dns_address = dnsConf._3
    china_dns_port = dnsConf._4
    // v2ray ipv6
    if (profile.isV2Ray) {
      if (!Utils.isNumeric(profile.v_add)) Utils.resolve(profile.v_add, enableIPv6 = profile.ipv6, hostname=dns_address) match {
        case Some(addr) => profile.v_add = addr
        case None => throw NameNotResolvedException()
      }
      v2rayThread = new V2RayVpnThread(this)
      v2rayThread.start()
      return
    }

    vpnThread = new ShadowsocksVpnThread(this)
    vpnThread.start()

    // reset the context
    killProcesses()

    // Resolve the server address
    host_arg = profile.host
    if (!Utils.isNumeric(profile.host)) Utils.resolve(profile.host, enableIPv6 = true, hostname=dns_address) match {
      case Some(addr) => profile.host = addr
      case None => throw NameNotResolvedException()
    }

    handleConnection()
    // change state
    changeState(State.CONNECTED)

//    if (profile.route != Route.ALL)
//      AclSyncJob.schedule(profile.route)

    notification = new ShadowsocksNotification(this, profile.name, "service-ssr")
  }

  /** Called when the activity is first created. */
  def handleConnection() {

    val fd = startVpn()
    if (!sendFd(fd)) throw new Exception("sendFd failed")

    startShadowsocksDaemon()

    if (profile.udpdns) {
      startShadowsocksUDPDaemon()
    }

    if (!profile.udpdns) {
      startDnsDaemon()
      startDnsTunnel()
    }
  }


  def startShadowsocksUDPDaemon() {
    val conf = ConfigUtils
      .SHADOWSOCKS.formatLocal(Locale.ENGLISH, profile.host, profile.remotePort, profile.localPort,
        ConfigUtils.EscapedJson(profile.password), profile.method, 600, profile.protocol, profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param), ConfigUtils.EscapedJson(profile.protocol_param))
    Utils.printToFile(new File(getApplicationInfo.dataDir + "/ss-local-udp-vpn.conf"))(p => {
      p.println(conf)
    })

    //val old_ld = Os.getenv("LD_PRELOAD")

    //Os.setenv("LD_PRELOAD", getApplicationInfo.dataDir + "/lib/libproxychains4.so", true)
    //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo.dataDir + "/proxychains.conf", true)

    var cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL), "-V", "-U"
      , "-b", "127.0.0.1"
      , "-t", "600"
      , "--host", host_arg
      , "-P", getApplicationInfo.dataDir
      , "-c", getApplicationInfo.dataDir + "/ss-local-udp-vpn.conf")

    if (proxychains_enable) {
      cmd prepend "LD_PRELOAD=" + Utils.getAbsPath(ExeNative.PROXYCHAINS)
      cmd prepend "PROXYCHAINS_CONF_FILE=" + getApplicationInfo.dataDir + "/proxychains.conf"
      cmd prepend "PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo.dataDir
      cmd prepend "env"
    }

    if (BuildConfig.DEBUG) Log.d(TAG, cmd.mkString(" "))

    sstunnelProcess = new GuardedProcess(cmd).start()

    //Os.setenv("LD_PRELOAD", old_ld, true)
  }

  def startShadowsocksDaemon() {

    val conf = ConfigUtils
      .SHADOWSOCKS.formatLocal(Locale.ENGLISH, profile.host, profile.remotePort, profile.localPort,
        ConfigUtils.EscapedJson(profile.password), profile.method, 600, profile.protocol, profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param), ConfigUtils.EscapedJson(profile.protocol_param))
    Utils.printToFile(new File(getApplicationInfo.dataDir + "/ss-local-vpn.conf"))(p => {
      p.println(conf)
    })

    //val old_ld = Os.getenv("LD_PRELOAD")

    //Os.setenv("LD_PRELOAD", getApplicationInfo.dataDir + "/lib/libproxychains4.so", true)
    //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo.dataDir + "/proxychains.conf", true)

    val cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL), "-V", "-x"
      , "-b", "127.0.0.1"
      , "-t", "600"
      , "--host", host_arg
      , "-P", getApplicationInfo.dataDir
      , "-c", getApplicationInfo.dataDir + "/ss-local-vpn.conf")

    if (profile.udpdns) cmd += "-u"

    if (profile.route != Route.ALL) {
      cmd += "--acl"
      cmd += getApplicationInfo.dataDir + '/' + profile.route + ".acl"
    }

    if (TcpFastOpen.sendEnabled) cmd += "--fast-open"

    if (proxychains_enable) {
      cmd prepend "LD_PRELOAD=" + Utils.getAbsPath(ExeNative.PROXYCHAINS)
      cmd prepend "PROXYCHAINS_CONF_FILE=" + getApplicationInfo.dataDir + "/proxychains.conf"
      cmd prepend "PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo.dataDir
      cmd prepend "env"
    }

    if (BuildConfig.DEBUG) Log.d(TAG, cmd.mkString(" "))

    sslocalProcess = new GuardedProcess(cmd).start()

    //Os.setenv("LD_PRELOAD", old_ld, true)
  }

  def startDnsTunnel() = {
    val conf = ConfigUtils
      .SHADOWSOCKS.formatLocal(Locale.ENGLISH, profile.host, profile.remotePort, profile.localPort + 63,
        ConfigUtils.EscapedJson(profile.password), profile.method, 600, profile.protocol, profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param), ConfigUtils.EscapedJson(profile.protocol_param))
    Utils.printToFile(new File(getApplicationInfo.dataDir + "/ss-tunnel-vpn.conf"))(p => {
      p.println(conf)
    })

    //val old_ld = Os.getenv("LD_PRELOAD")

    //Os.setenv("LD_PRELOAD", getApplicationInfo.dataDir + "/lib/libproxychains4.so", true)
    //Os.setenv("PROXYCHAINS_CONF_FILE", getApplicationInfo.dataDir + "/proxychains.conf", true)

    val cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL)
      , "-V"
      , "-u"
      , "-t", "60"
      , "--host", host_arg
      , "-b", "127.0.0.1"
      , "-P", getApplicationInfo.dataDir
      , "-c", getApplicationInfo.dataDir + "/ss-tunnel-vpn.conf")

    cmd += "-L"
    if (profile.route == Route.CHINALIST)
      cmd += china_dns_address + ":" + china_dns_port.toString
    else
      cmd += dns_address + ":" + dns_port.toString

    if (proxychains_enable) {
      cmd prepend "LD_PRELOAD=" + Utils.getAbsPath(ExeNative.PROXYCHAINS)
      cmd prepend "PROXYCHAINS_CONF_FILE=" + getApplicationInfo.dataDir + "/proxychains.conf"
      cmd prepend "PROXYCHAINS_PROTECT_FD_PREFIX=" + getApplicationInfo.dataDir
      cmd prepend "env"
    }

    if (BuildConfig.DEBUG) Log.d(TAG, cmd.mkString(" "))

    sstunnelProcess = new GuardedProcess(cmd).start()

    //Os.setenv("LD_PRELOAD", old_ld, true)
  }

  def startDnsDaemon() {
    val reject = if (profile.ipv6) "224.0.0.0/3" else "224.0.0.0/3, ::/0"
    val protect = "protect = \"" + protectPath +"\";"

    var china_dns_settings = ""

    var remote_dns = false

    if (profile.route == Route.ACL) {
        //decide acl route
        val total_lines = Source.fromFile(new File(getApplicationInfo.dataDir + '/' + profile.route + ".acl")).getLines()
        total_lines.foreach((line: String) => {
            if (line.equals("[remote_dns]")) {
                remote_dns = true
            }
        })
    }

    val (black_list, black_list_cn) = profile.route match {
      case Route.BYPASS_CHN | Route.BYPASS_LAN_CHN | Route.GFWLIST |
           Route.ACL4SSR_BANDAD | Route.ACL4SSR_GFWLIST_BANAD | Route.ACL4SSR_ONLYBANAD |
           Route.ACL4SSR_FULLGFWLIST | Route.ACL4SSR_BACKCN_BANAD | Route.ACL4SSR_NOBANAD => {
        (getBlackList(), getBlackList("cn"))
      }
      case Route.ACL => {
        if (remote_dns) {
          ("", "")
        } else {
          (getBlackList(), getBlackList("cn"))
        }
      }
      case _ => {
        ("", "")
      }
    }

//    for (china_dns <- profile.china_dns.split(",")) {
//      china_dns_settings += ConfigUtils.REMOTE_SERVER.formatLocal(Locale.ENGLISH, china_dns.split(":")(0), china_dns.split(":")(1).toInt,
//        black_list, reject)
//    }
    var dns_addr = profile.china_dns.split(",").head
    china_dns_settings += ConfigUtils.REMOTE_SERVER.formatLocal(Locale.ENGLISH, dns_addr.split(":")(0), dns_addr.split(":")(1).toInt, black_list, reject)
    dns_addr = profile.dns.split(",").head
    china_dns_settings += ConfigUtils.REMOTE_SERVER.formatLocal(Locale.ENGLISH, dns_addr.split(":")(0), dns_addr.split(":")(1).toInt, black_list_cn, reject)

    val nocache = app.appStateManager.getAppState()
      .flatMap(appState => if(appState.dns_nocache == "off") Option(s"nocache = on;") else None).getOrElse("")
    val conf = profile.route match {
      case Route.BYPASS_CHN | Route.BYPASS_LAN_CHN | Route.GFWLIST |
           Route.ACL4SSR_BANDAD | Route.ACL4SSR_GFWLIST_BANAD | Route.ACL4SSR_ONLYBANAD |
           Route.ACL4SSR_FULLGFWLIST | Route.ACL4SSR_BACKCN_BANAD | Route.ACL4SSR_NOBANAD => {
        ConfigUtils.PDNSD_DIRECT.formatLocal(Locale.ENGLISH, protect, getApplicationInfo.dataDir,
          "0.0.0.0", profile.localPort + 53, nocache, china_dns_settings, profile.localPort + 63, reject)
      }
      case Route.CHINALIST => {
        ConfigUtils.PDNSD_DIRECT.formatLocal(Locale.ENGLISH, protect, getApplicationInfo.dataDir,
          "0.0.0.0", profile.localPort + 53, nocache, china_dns_settings, profile.localPort + 63, reject)
      }
      case Route.ACL => {
        if (!remote_dns) {
            ConfigUtils.PDNSD_DIRECT.formatLocal(Locale.ENGLISH, protect, getApplicationInfo.dataDir,
              "0.0.0.0", profile.localPort + 53, nocache, china_dns_settings, profile.localPort + 63, reject)
        } else {
            ConfigUtils.PDNSD_LOCAL.formatLocal(Locale.ENGLISH, protect, getApplicationInfo.dataDir,
              "0.0.0.0", profile.localPort + 53, nocache, profile.localPort + 63, reject)
        }
      }
      case _ => {
        ConfigUtils.PDNSD_LOCAL.formatLocal(Locale.ENGLISH, protect, getApplicationInfo.dataDir,
          "0.0.0.0", profile.localPort + 53, nocache, profile.localPort + 63, reject)
      }
    }

    // Log.e(TAG, s"conf: $conf")
    Utils.printToFile (new File(getApplicationInfo.dataDir + "/pdnsd-vpn.conf"))(p => {
      p.println(conf)
      Route.BLOCK_DOMAIN.foreach(domain => p.println(s"neg { name = $domain; types = domain; }"))
      app.BLOCK_DOMAIN.foreach(domain => p.println(s"neg { name = $domain; types = A,AAAA; }"))
    })
    val cmd = Array(Utils.getAbsPath(ExeNative.PDNSD), "-c", getApplicationInfo.dataDir + "/pdnsd-vpn.conf")

    if (BuildConfig.DEBUG) Log.d(TAG, cmd.mkString(" "))

    pdnsdProcess = new GuardedProcess(cmd).start()
  }

  def initVPNBuilder(): Builder = {
    val builder = new Builder()
    builder
      .setSession(profile.name)
      .setMtu(VPN_MTU)
      .addAddress(PRIVATE_VLAN.formatLocal(Locale.ENGLISH, "1"), 24)

    if (profile.route == Route.CHINALIST)
      builder.addDnsServer(china_dns_address)
    else
      builder.addDnsServer(dns_address)

    if (profile.ipv6) {
      builder.addAddress(PRIVATE_VLAN6.formatLocal(Locale.ENGLISH, "1"), 126)
      builder.addRoute("::", 0)
    }

    if (Utils.isLollipopOrAbove) {

      // TODO: Content Provider
      val appState = app.appStateManager.getAppState()
      val isPerAppProxyEnabled = appState.map(_.per_app_proxy_enable).getOrElse(false)
      if (isPerAppProxyEnabled) {
        val bypassMode = appState.map(_.bypass_mode).getOrElse(false)
        val packageNames = appState.map(_.package_names).getOrElse("")
        if (!bypassMode) builder.addAllowedApplication(getPackageName)
        for (pkg <- packageNames.split('\n')) {
          try {
            if (!bypassMode) {
              builder.addAllowedApplication(pkg)
            }
            if (bypassMode && pkg != getPackageName) {
              builder.addDisallowedApplication(pkg)
            }
          } catch {
            case ex: NameNotFoundException =>
              Log.e(TAG, "Invalid package name", ex)
          }
        }
      }
    }

    if (profile.route == Route.ALL || profile.route == Route.BYPASS_CHN) {
      builder.addRoute("0.0.0.0", 0)
    } else {
      val privateList = getResources.getStringArray(R.array.bypass_private_route)
      privateList.foreach(cidr => {
        val addr = cidr.split('/')
        builder.addRoute(addr(0), addr(1).toInt)
      })
    }

    if (profile.route == Route.CHINALIST)
      builder.addRoute(china_dns_address, 32)
    else
      builder.addRoute(dns_address, 32)
    if (Build.VERSION.SDK_INT >= 29) {
      val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
      builder.setMetered(cm.isActiveNetworkMetered)
    }
    builder
  }

  @SuppressLint(Array("NewApi"))
  def startVpn(): Int = {

    val builder = initVPNBuilder()
    conn = builder.establish()
    if (conn == null) throw new NullConnectionException

    val fd = conn.getFd

    var cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.TUN2SOCKS),
      "--netif-ipaddr", PRIVATE_VLAN.formatLocal(Locale.ENGLISH, "2"),
      "--netif-netmask", "255.255.255.0",
      "--socks-server-addr", "127.0.0.1:" + profile.localPort,
      "--tunfd", fd.toString,
      "--tunmtu", VPN_MTU.toString,
      "--sock-path", getApplicationInfo.dataDir + "/sock_path",
      "--loglevel", "3")

    if (profile.ipv6)
      cmd += ("--netif-ip6addr", PRIVATE_VLAN6.formatLocal(Locale.ENGLISH, "2"))

    if (profile.udpdns)
      cmd += "--enable-udprelay"
    else
      cmd += ("--dnsgw", "%s:%d".formatLocal(Locale.ENGLISH, PRIVATE_VLAN.formatLocal(Locale.ENGLISH, "1"),
        profile.localPort + 53))

    if (BuildConfig.DEBUG) Log.d(TAG, cmd.mkString(" "))

    tun2socksProcess = new GuardedProcess(cmd).start(() => sendFd(fd))

    fd
  }

  def sendFd(fd: Int): Boolean = {
    if (fd != -1) {
      var tries = 1
      while (tries < 5) {
        Thread.sleep(80 >> tries)
        if (System.sendfd(fd, getApplicationInfo.dataDir + "/sock_path") != -1) {
          return true
        }
        tries += 1
      }
    }
    false
  }

  class NetworkConnectivityMonitor extends ConnectivityManager.NetworkCallback {
    lazy val connectivityManager = getSystemService(classOf[ConnectivityManager])
    var underlyingNetwork : Network = _

    private[this] def getUnderlyingNetworks(network: Network): Array[Network] = {
      if (Build.VERSION.SDK_INT == 28 && connectivityManager.isActiveNetworkMetered) null
      else Array(network)
    }
    override def onAvailable(network: Network): Unit = {
      val networkInfo = connectivityManager.getNetworkInfo(network)
      if (networkInfo == null || networkInfo.getState() != NetworkInfo.State.CONNECTED) {
        return
      }
      val networks = getUnderlyingNetworks(network)
      if (Build.VERSION.SDK_INT >= 22) setUnderlyingNetworks(networks)
    }

    override def onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities): Unit = {
      val networks = getUnderlyingNetworks(network)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) setUnderlyingNetworks(networks)
    }

    override def onLost(network: Network): Unit = {
      val activeNetworkInfo = connectivityManager.getActiveNetworkInfo
      if (activeNetworkInfo != null && activeNetworkInfo.getState == NetworkInfo.State.CONNECTED) {
          return
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) setUnderlyingNetworks(null)
    }
  }

  def startNetworkConnectivityMonitor(): Unit = {

  }


  def stopNetworkConnectivityMonitor (): Unit = {
  }
}


