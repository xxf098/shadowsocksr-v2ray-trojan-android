package com.github.shadowsocks.services

import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.concurrent.{CountDownLatch, TimeUnit}

import android.app.{NotificationManager, PendingIntent, Service, Notification}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.{Build, Bundle, Environment, IBinder, Looper, ResultReceiver}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.{Base64, Log}
import android.widget.Toast
import com.github.shadowsocks.{GuardedProcess, ProfileManagerActivity, R}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.{Profile, VmessQRCode}
import com.github.shadowsocks.utils.{Action, ConfigUtils, ExeNative, Key, TcpFastOpen, TrafficMonitor, Utils}
import tun2socks.Tun2socks
import java.util.{Date, Locale}

import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.support.v4.content.FileProvider

object DownloadTestService {
  val NOTIFICATION_ID = 2
  val RESULT_NOTIFICATION_ID = 3
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
    val isTestBoth = intent.getBooleanExtra("is_test_both", false)
    bgResultReceiver = intent.getParcelableExtra("BgResultReceiver")
    val testProfiles = Option(ProfileManagerActivity.getProfilesByGroup(currentGroupName, isSort, false))
    testProfiles match {
      //      app.profileManager.getAllProfiles match {
      case Some(profiles) =>

        isTesting = true

        val testDownloadJob = (v2rayProfiles: List[Profile]) => {
          val links = v2rayProfiles.map {
            case p if p.isTrojan => s"trojan://${p.t_password}@${p.t_addr}:${p.t_port}?sni=${p.t_peer}&allowInsecure=${if(p.t_allowInsecure) 1 else 0}"
            case p if p.isVmess => VmessQRCode(p.v_v, p.name, p.v_add, p.v_port, p.v_id, p.v_aid, p.v_net, p.v_type, p.v_host, p.v_path, p.v_tls, p.v_security,null,p.v_security,"", p.t_allowInsecure).toString
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
            // index, speed
            override def updateSpeed(l: Long, l1: Long, elapse: Long): Unit = {
              val p = v2rayProfiles(l.toInt)
              p.download_speed = l1
              if (isTestBoth) {  p.elapsed = elapse }
              app.profileManager.updateProfile(p)
              val speed = TrafficMonitor.formatTrafficInternal(l1, true)
              Log.e(TAG, s"ps: ${p.name} index: ${l}, server: ${p.v_add} download_speed: ${speed}")
              counter = counter + 1
              val contextText = if (isTestBoth) s"$speed/${elapse}ms" else speed;
              updateNotification(p.name, contextText, max, counter, isTestBoth)
            }

            override def updateTraffic(l: Long, l1: Long): Unit = {
              totalTraffic += l1
              val traffic = TrafficMonitor.formatTrafficInternal(totalTraffic)
//              Log.e(TAG, s"traffic: $traffic")
              updateTrafficInfo(traffic)
            }
          }
          val concurrency = app.settings.getInt(Key.TEST_CONCURRENCY, 2)
          val dateFormat = new SimpleDateFormat("yyyyMMddhhmmss")
          val date = dateFormat.format(new Date())
          val pngPath = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath + s"/litespeedtest_$date.png"
          val fontPath = app.getFontAssetsPath()
//          Tun2socks.batchTestDownload(links, concurrency, new TestDownloadUpdate())
          val groupName = v2rayProfiles.head.url_group
          val language = if (Locale.getDefault.getCountry == "CN") "cn" else "en"
          Tun2socks.batchRenderTestDownload(links, concurrency, fontPath, pngPath, language, groupName, new TestDownloadUpdate())
          val countDownLatch = new CountDownLatch(1)
          MediaScannerConnection.scanFile(this, Array(pngPath), null, new MediaScannerConnection.OnScanCompletedListener {
            override def onScanCompleted(path: String, uri: Uri): Unit = {
//              Log.e(TAG, path)
              countDownLatch.countDown()
            }
          })
          countDownLatch.await(2400, TimeUnit.MILLISECONDS)
          showResultNotification(pngPath)
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
//    val pngPath = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath + s"/speedtest_20210612022313.png"
//    showResultNotification(pngPath)
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
    notificationService.notify(DownloadTestService.NOTIFICATION_ID, builder.build())
  }

  private def showResultNotification (pngPath: String): Unit = {
    val bitmap = BitmapFactory.decodeFile(pngPath)
    val file = new File(pngPath)
    builder = new NotificationCompat.Builder(this, "service-test")
      .setColor(ContextCompat.getColor(this, R.color.material_accent_500))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setSmallIcon(R.drawable.ic_click_white)
      .setAutoCancel(false)
      .setContentTitle(getString(R.string.view_test_result))
      .setContentText(file.getName)
      .setAutoCancel(true)
      .setLargeIcon(bitmap)
    val intent = openPng(this, file)
    val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    builder.setContentIntent(pendingIntent)
    notificationService.notify(DownloadTestService.RESULT_NOTIFICATION_ID, builder.build())
  }

  private  def openPng (context: Context, file: File): Intent = {
    //  exposed beyond app through Intent.getData()
    val intent = new Intent(Intent.ACTION_VIEW)
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//    val file = new File(pngPath)
    var data = Uri.fromFile(file)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      data = FileProvider.getUriForFile(context, "com.xxf098.ssrray.fileprovider", file)
    }
//    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.setDataAndType(data, "image/*")
    intent
  }

  private def updateNotification (title: String, speed: String, max: Int, counter: Int, isTestBoth: Boolean): Unit = {
    if (!isTesting) return
    //    val formatTitle = title.substring(0, 16) + "  " + latency.getOrElse("0ms")
    //    Log.e(TAG, s"formatTitle: $formatTitle")
    val max_length = if (isTestBoth) 15 else 20
    val length = math.min(title.length, max_length)
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

