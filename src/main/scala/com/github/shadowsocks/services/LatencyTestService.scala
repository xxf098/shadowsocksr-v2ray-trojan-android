package com.github.shadowsocks.services

import java.io.File
import java.net.Socket
import java.util.Locale

import android.app.{ProgressDialog, Service}
import android.content.DialogInterface.OnCancelListener
import android.content.{DialogInterface, Intent}
import android.os.{IBinder, Looper, Message}
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.github.shadowsocks.GuardedProcess
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.VmessAction.profile
import com.github.shadowsocks.utils.{Action, ConfigUtils, ExeNative, Key, TcpFastOpen, Utils}
import tun2socks.Tun2socks
import com.github.shadowsocks.R

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.ExecutionContext.Implicits.global

class LatencyTestService extends Service {
  val TAG = "LatencyTestService"
  var isTesting = false
  private var ssTestProcess: GuardedProcess = _
  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (isTesting) stopSelf(startId)
    isTesting = true
    val currentGroupName = intent.getStringExtra(Key.currentGroupName)
    val testProfiles = if (currentGroupName == getString(R.string.allgroups)) app.profileManager.getAllProfiles
    else app.profileManager.getAllProfilesByGroup(currentGroupName)
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
                })
            })
            // TODO: Duration
            Await.ready(Future.sequence(futures), Duration(20, SECONDS)).onFailure{
              case e: Exception => e.printStackTrace()
            }
          })
        }

        val testV2rayJob = (v2rayProfiles: List[Profile]) => {
          testV2rayProfiles(v2rayProfiles.grouped(4).toList, 4)
          val zeroV2RayProfiles = v2rayProfiles.filter(p => p.elapsed == 0 && p.isV2Ray)
          if (zeroV2RayProfiles.nonEmpty) {
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
                testResult
              }))
              Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
                case e: Exception => e.printStackTrace()
              }
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
            })
            Await.ready(Future.sequence(futures), Duration(5 * size, SECONDS)).onFailure{
              case e: Exception => e.printStackTrace()
            }
          })
        }

        val testSSRJob = (ssrProfiles: List[Profile]) => {
          val pingMethod = app.settings.getString(Key.PING_METHOD, "google")
          val pingFunc = if (pingMethod == "google") testSSRProfiles else testTCPSSRProfiles
          pingFunc(ssrProfiles.grouped(4).toList, 4, ssrProfiles.size)
          val zeroSSRProfiles = ssrProfiles.filter(p => p.elapsed == 0 && !p.isV2Ray)
          if (zeroSSRProfiles.nonEmpty) {
            pingFunc(zeroSSRProfiles.grouped(2).toList, 2, ssrProfiles.size)
          }
        }
        val testJob = new Thread {
          override def run() {
            // Do some background work
            Looper.prepare()
            val (v2rayProfiles, ssrProfiles) = profiles
              .filter(p => !List("www.google.com", "127.0.0.1", "8.8.8.8", "1.2.3.4", "1.1.1.1").contains(p.host))
              .partition(_.isV2Ray)
            if (v2rayProfiles.nonEmpty) { testV2rayJob(v2rayProfiles) }
            if (ssrProfiles.nonEmpty) { testSSRJob(ssrProfiles) }
            Looper.loop()
          }
        }
        testJob.start()

      case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
    }

    return Service.START_NOT_STICKY
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

    return result;
  }
}
