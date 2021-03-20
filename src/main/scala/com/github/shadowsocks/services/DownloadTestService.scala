package com.github.shadowsocks.services

import java.nio.charset.Charset
import android.app.{NotificationManager, PendingIntent, Service}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.{Bundle, IBinder, Looper, ResultReceiver}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.{Base64, Log}
import android.widget.Toast
import com.github.shadowsocks.{GuardedProcess, ProfileManagerActivity, R}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.{Profile, VmessQRCode}
import com.github.shadowsocks.utils.{Action, ConfigUtils, ExeNative, Key, TcpFastOpen, TrafficMonitor, Utils}
import tun2socks.Tun2socks
import java.util.Locale

object DownloadTestService {
  val NOTIFICATION_ID = 2
}

class DownloadTestService extends Service {
  val TAG = "DownloadTestService"
  var isTesting = false
  private var bgResultReceiver: ResultReceiver = _
  private lazy val notificationService = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
  private var builder: NotificationCompat.Builder = _
  private var counter = 0
  private var max = 0
  private var totalTraffic = 0L
  private var stopReceiverRegistered = false
  private lazy val stopReceiver: BroadcastReceiver = (context: Context, intent: Intent) => {
    Toast.makeText(context, R.string.stopping, Toast.LENGTH_SHORT).show()
    stopTest()
  }
  private var testJob: Thread = _

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (isTesting) stopSelf(startId)
    isTesting = true
    if (!stopReceiverRegistered) {
      val filter = new IntentFilter()
      filter.addAction(Action.STOP_TEST)
      registerReceiver(stopReceiver, filter)
      stopReceiverRegistered = true
    }
    // TODO: test options
    val currentGroupName = intent.getStringExtra(Key.currentGroupName)
    val isSort = intent.getBooleanExtra("is_sort", false)
    bgResultReceiver = intent.getParcelableExtra("BgResultReceiver")
    val testProfiles = Option(ProfileManagerActivity.getProfilesByGroup(currentGroupName, isSort))
    testProfiles match {
      //      app.profileManager.getAllProfiles match {
      case Some(profiles) =>

        isTesting = true

        val testDownloadJob = (v2rayProfiles: List[Profile]) => {
          val links = v2rayProfiles.map {
            case p if p.isTrojan => s"trojan://${p.t_password}@${p.t_addr}:${p.t_port}?sni=${p.t_peer}"
            case p if p.isVmess => VmessQRCode(p.v_v, "", p.v_add, p.v_port, p.v_id, p.v_aid, p.v_net, p.v_type, p.v_host, p.v_path, p.v_tls, "").toString
            case p if p.isShadowSocks => {
              implicit val flags: Int = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP
              val data = s"${p.v_security}:${p.v_id}".getBytes(Charset.forName("UTF-8"))
              s"ss://${Utils.b64Encode(data)}@${p.v_add}:${p.v_port}"
            }
            case p => {
              implicit val flags: Int = Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_WRAP
              "ssr://" + Utils.b64Encode("%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s".formatLocal(Locale.ENGLISH,
                p.host, p.remotePort, p.protocol, p.method, p.obfs,
                Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, p.password).getBytes),
                Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, p.obfs_param).getBytes),
                Utils.b64Encode("%s".formatLocal(Locale.ENGLISH, p.protocol_param).getBytes)).getBytes)
            }
          }.mkString(",")
          class TestDownloadUpdate extends tun2socks.TestDownload {
            override def updateSpeed(l: Long, l1: Long): Unit = {
              val p = v2rayProfiles(l.toInt)
              p.download_speed = l1
              app.profileManager.updateProfile(p)
              val speed = TrafficMonitor.formatTrafficInternal(l1, true)
              Log.e(TAG, s"ps: ${p.name} index: ${l}, server: ${p.v_add} download_speed: ${speed}")
              counter = counter + 1
              updateNotification(p.name, speed, max, counter)
            }

            override def updateTraffic(l: Long, l1: Long): Unit = {
              totalTraffic += l1
              val traffic = TrafficMonitor.formatTrafficInternal(totalTraffic)
              Log.e(TAG, s"traffic: $traffic")
              updateTrafficInfo(traffic)
            }
          }
          Tun2socks.batchTestDownload(links, 2, new TestDownloadUpdate())
        }

        testJob = new Thread {
          override def run() {
            try {
              Looper.prepare()
              val downloadProfiles = profiles
                .filter(p => !List("www.google.com", "127.0.0.1", "8.8.8.8", "1.2.3.4", "1.1.1.1").contains(p.host))
              max = profiles.size
              if (downloadProfiles.nonEmpty) { testDownloadJob(downloadProfiles) }
            } catch {
              case e: Exception => e.printStackTrace()
            } finally {
              isTesting = false
              notificationService.cancel(DownloadTestService.NOTIFICATION_ID)
              bgResultReceiver.send(100, new Bundle())
              stopSelf(startId)
              Looper.loop()
            }
          }
        }
        testJob.start()

      case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
    }
    showNotification(testProfiles.size)
    Service.START_NOT_STICKY
  }

  private def stopTest(): Unit = {
    isTesting = false
    notificationService.cancel(DownloadTestService.NOTIFICATION_ID)
    stopSelf()
  }

  override def onDestroy(): Unit = {
    if (testJob != null) testJob.interrupt()
    if (stopReceiverRegistered) {
      unregisterReceiver(stopReceiver)
      stopReceiverRegistered = false
    }
    super.onDestroy()
  }

  private def showNotification (max: Int): Unit = {
    builder = new NotificationCompat.Builder(this, "service-test")
      .setSubText("0 B")
      .setColor(ContextCompat.getColor(this, R.color.material_accent_500))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setSmallIcon(R.drawable.ic_click_white)
      .setAutoCancel(false)
      .setOngoing(true)
      .setContentTitle(getString(R.string.service_test_working))
      .setProgress(max, 0, false)
    val stopAction = new NotificationCompat.Action.Builder(
      R.drawable.ic_navigation_close,
      getString(R.string.stop),
      PendingIntent.getBroadcast(this, 0, new Intent(Action.STOP_TEST).setPackage(getPackageName), 0)
    ).build()
    builder.addAction(stopAction)
    notificationService.notify(LatencyTestService.NOTIFICATION_ID, builder.build())
  }

  private def updateNotification (title: String, speed: String, max: Int, counter: Int): Unit = {
    if (!isTesting) return
    //    val formatTitle = title.substring(0, 16) + "  " + latency.getOrElse("0ms")
    //    Log.e(TAG, s"formatTitle: $formatTitle")
    val length = math.min(title.length, 20)
    builder.setContentTitle(title.substring(0, length))
      .setContentText(speed)
      .setProgress(max, counter, false)
    notificationService.notify(LatencyTestService.NOTIFICATION_ID, builder.build())
  }

  private def updateTrafficInfo (traffic: String): Unit = {
    if (!isTesting) return
    builder.setSubText(traffic)
    notificationService.notify(LatencyTestService.NOTIFICATION_ID, builder.build())
  }
}

