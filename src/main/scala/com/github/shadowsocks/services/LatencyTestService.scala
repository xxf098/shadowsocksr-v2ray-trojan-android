package com.github.shadowsocks.services

import java.io.File
import java.net.Socket
import java.util
import java.util.Locale

import android.app.{NotificationManager, PendingIntent, ProgressDialog, Service}
import android.content.DialogInterface.OnCancelListener
import android.content.{BroadcastReceiver, Context, DialogInterface, Intent, IntentFilter}
import android.os.{Bundle, IBinder, Looper, Message, ResultReceiver}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.github.shadowsocks.{GuardedProcess, ProfileManagerActivity, R}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.VmessAction.profile
import com.github.shadowsocks.utils.{Action, ConfigUtils, ExeNative, Key, TcpFastOpen, Utils}
import tun2socks.Tun2socks

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.ExecutionContext.Implicits.global

object LatencyTestService {
  val NOTIFICATION_ID = 2
}

class LatencyTestService extends Service {
  val TAG = "LatencyTestService"
  var isTesting = false
  private var ssTestProcess: GuardedProcess = _
  private var bgResultReceiver: ResultReceiver = _
  private lazy val notificationService = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
  private var builder: NotificationCompat.Builder = _
  private var counter = 0
  private var max = 0
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
    val currentGroupName = intent.getStringExtra(Key.currentGroupName)
    val isSort = intent.getBooleanExtra("is_sort", false)
    bgResultReceiver = intent.getParcelableExtra("BgResultReceiver")
    val testProfiles = Option(ProfileManagerActivity.getProfilesByGroup(currentGroupName, isSort))
    testProfiles match {
      //      app.profileManager.getAllProfiles match {
      case Some(profiles) =>

        isTesting = true

        val testV2rayProfiles = (v2rayProfiles: List[List[Profile]], size: Int) => {
          val pingMethod = app.settings.getString(Key.PING_METHOD, "google")
          v2rayProfiles.indices.foreach(index => {
            val profiles = v2rayProfiles(index)
            val futures = profiles.indices.map(i =>{
              val p = profiles(i)
              Future(p.pingItemThread(pingMethod, 8900L + index * size + i))
                .map(testResult => {
                  Log.e(TAG, s"testResult: ${testResult}")
                  counter += 1
                  updateNotification(p.name, testResult, max, counter)
                })
            })
            // TODO: Duration
            Await.ready(Future.sequence(futures), Duration(20, SECONDS)).onFailure{
              case e: Exception => e.printStackTrace()
            }
            sendProfileIds(profiles)
            Log.e(TAG, s"testResult: ${index}")
          })
        }

        val testV2rayJob = (v2rayProfiles: List[Profile]) => {
          max = v2rayProfiles.size * 3 / 2
          testV2rayProfiles(v2rayProfiles.grouped(4).toList, 4)
          val zeroV2RayProfiles = v2rayProfiles.filter(p => p.elapsed == 0 && p.isV2Ray)
          if (zeroV2RayProfiles.nonEmpty) {
            max = v2rayProfiles.size + zeroV2RayProfiles.size
            testV2rayProfiles(zeroV2RayProfiles.grouped(2).toList, 2)
          }
        }

        // TODO: refactor
        // connection pool time
        val testSSRProfiles = (ssrProfiles: List[List[Profile]], size: Int, offset: Int) => {
          ssrProfiles.indices.foreach(index => {
            val profiles: List[Profile] = ssrProfiles(index)
            try {
              val confServer = profiles.indices.map(i => {
                val profile = profiles(i)
                var host = profile.host
                if (!Utils.isNumeric(host)) Utils.resolve(host, enableIPv6 = false) match {
                  case Some(addr) => host = addr
                  case None => host = "127.0.0.1"
                }
                ConfigUtils.SHADOWSOCKSR_TEST_SERVER.formatLocal(Locale.ENGLISH,
                  s"${host}${profile.remotePort}", host, profile.remotePort, profile.localPort + index * size + i + offset, ConfigUtils.EscapedJson(profile.password), profile.method,
                  profile.protocol, ConfigUtils.EscapedJson(profile.protocol_param), profile.obfs, ConfigUtils.EscapedJson(profile.obfs_param)
                )
              }).mkString(",")
              val confTest = ConfigUtils.SHADOWSOCKSR_TEST_CONF.formatLocal(Locale.ENGLISH,
                confServer, 600, "www.google.com:80")
              Utils.printToFile(new File(getApplicationInfo.dataDir + "/ss-local-test.conf"))(p => {
                p.println(confTest)
              })

              val cmd = ArrayBuffer[String](Utils.getAbsPath(ExeNative.SS_LOCAL)
                , "-t", "600"
                , "-L", "www.google.com:80"
                , "-c", getApplicationInfo.dataDir + "/ss-local-test.conf")

              if (TcpFastOpen.sendEnabled) cmd += "--fast-open"

              if (ssTestProcess != null) {
                ssTestProcess.destroy()
                ssTestProcess = null
              }

              ssTestProcess = new GuardedProcess(cmd).start()

              var start = 0
              while (start < 2 && isPortAvailable(profiles.head.localPort + index * size + offset)) {
                try {
                  start = start + 1
                  Thread.sleep(50)
                } catch {
                  case e: InterruptedException => isTesting = false
                }
              }

              val futures = profiles.indices.map(i => Future {
                var result = ""
                val profile = profiles(i)
                // TODO: batch test with go
                val elapsed = Tun2socks.testURLLatency("http://127.0.0.1:" + (profile.localPort + index * size + i + offset) + "/generate_204")
                result = getString(R.string.connection_test_available, elapsed: java.lang.Long)
                profile.elapsed = elapsed
                // Log.e(TAG, s"host:${profile.host}, elapsed: $elapsed")
                app.profileManager.updateProfile(profile)
                result
              }.recover {
                case e: Exception => {
                  val profile = profiles(i)
                  Log.e(TAG, s"host: ${profile.host}, msg: ${e.getMessage}")
                  profile.elapsed = 0
                  app.profileManager.updateProfile(profile)
                  e.getMessage
                }
              }.map(testResult => {
                Log.e(TAG, s"testResult: ${testResult}")
                val p = profiles(i)
                counter += 1
                updateNotification(p.name, testResult, max, counter)
                testResult
              }))
              Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
                case e: Exception => e.printStackTrace()
              }
              sendProfileIds(profiles)
            } catch {
              case e: Exception => e.printStackTrace()
            }
            if (ssTestProcess != null) {
              ssTestProcess.destroy()
              ssTestProcess = null
            }
          })
        }

        val testTCPSSRProfiles = (ssrProfiles: List[List[Profile]], size: Int, offset: Int) => {
          ssrProfiles.indices.foreach(index => {
            val profiles: List[Profile] = ssrProfiles(index)
            val futures = profiles.map(p => Future {
              val testResult = p.testTCPLatencyThread()
              Log.e(TAG, s"testResult: ${testResult}")
              counter += 1
              updateNotification(p.name, testResult, max, counter)
            })
            Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
              case e: Exception => e.printStackTrace()
            }
            sendProfileIds(profiles)
            Log.e(TAG, s"testResult: ${index}")
          })
        }

        val testSSRJob = (ssrProfiles: List[Profile]) => {
          max = ssrProfiles.size * 3 / 2
          val pingMethod = app.settings.getString(Key.PING_METHOD, "google")
          val pingFunc = if (pingMethod == "google") testSSRProfiles else testTCPSSRProfiles
          pingFunc(ssrProfiles.grouped(4).toList, 4, ssrProfiles.size)
          val zeroSSRProfiles = ssrProfiles.filter(p => p.elapsed == 0 && !p.isV2Ray)
          if (zeroSSRProfiles.nonEmpty) {
            max = ssrProfiles.size + zeroSSRProfiles.size
            pingFunc(zeroSSRProfiles.grouped(2).toList, 2, ssrProfiles.size)
          }
        }
        testJob = new Thread {
          override def run() {
            try {
              Looper.prepare()
              val (v2rayProfiles, ssrProfiles) = profiles
                .filter(p => !List("www.google.com", "127.0.0.1", "8.8.8.8", "1.2.3.4", "1.1.1.1").contains(p.host))
                .partition(_.isV2Ray)
              if (v2rayProfiles.nonEmpty) { testV2rayJob(v2rayProfiles) }
              if (ssrProfiles.nonEmpty) { testSSRJob(ssrProfiles) }
              notificationService.cancel(LatencyTestService.NOTIFICATION_ID)
              // refresh
              bgResultReceiver.send(100, new Bundle())
              stopSelf(startId)
              Looper.loop()
            } catch {
              case e: Exception => e.printStackTrace()
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
    notificationService.cancel(LatencyTestService.NOTIFICATION_ID)
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

  def isPortAvailable (port: Int):Boolean = {
    // Assume no connection is possible.
    var result = true;

    try {
      (new Socket("127.0.0.1", port)).close()
      result = false;
    } catch {
      case e: Exception => Unit
    }

    return result
  }

  private def sendProfileIds (profiles: List[Profile]) = {
    val bundle = new Bundle()
    val ids = profiles.foldLeft(new util.ArrayList[Integer]())((b, p) => {
      b.add(p.id)
      b
    })
    bundle.putIntegerArrayList(Key.TEST_PROFILE_IDS, ids)
    bgResultReceiver.send(101, bundle)
  }

  private def showNotification (max: Int): Unit = {
    builder = new NotificationCompat.Builder(this, "service-test")
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

  private def updateNotification (title: String, testResult: String, max: Int, counter: Int): Unit = {
    val latency = """\d+ms""".r findFirstIn testResult
    val formatTitle = title.substring(0, 16) + "  " + latency.getOrElse("0ms")
    Log.e(TAG, s"formatTitle: $formatTitle")
    builder.setContentTitle(title.substring(0, 22))
      .setContentText(latency.getOrElse("0ms"))
      .setProgress(max, counter, false)
    notificationService.notify(LatencyTestService.NOTIFICATION_ID, builder.build())
  }
}
