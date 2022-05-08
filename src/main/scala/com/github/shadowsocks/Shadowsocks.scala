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

import java.io.IOException
import java.lang.System.currentTimeMillis
import java.net.{HttpURLConnection, URL}
import java.util
import java.util.concurrent.TimeUnit
import java.util.{GregorianCalendar, Locale}

import android.app.backup.BackupManager
import android.app.{Activity, NotificationManager, ProgressDialog}
import android.content._
import android.graphics.Typeface
import android.net.VpnService
import android.os._
import android.support.design.widget.{FloatingActionButton, Snackbar}
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.{View, ViewGroup, WindowManager}
import android.widget._
import com.github.jorgecastilloprz.FABProgressCircle
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback
import com.github.shadowsocks.database._
import com.github.shadowsocks.utils.CloseUtils._
import com.github.shadowsocks.utils._
import com.github.shadowsocks.job.SSRSubUpdateJob
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.types.{FailureConnect, Result, SuccessConnect}
import com.github.shadowsocks.utils.NetUtils.testConnectionStartup
import okhttp3._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Random, Try}
// TODO: route
// TODO: update shadowsocks-libev
object Typefaces {
  def get(c: Context, assetPath: String): Typeface = {
    cache synchronized {
      if (!cache.containsKey(assetPath)) {
        try {
          cache.put(assetPath, Typeface.createFromAsset(c.getAssets, assetPath))
        } catch {
          case e: Exception =>
            Log.e(TAG, "Could not get typeface '" + assetPath + "' because " + e.getMessage)
            app.track(e)
            return null
        }
      }
      return cache.get(assetPath)
    }
  }

  private final val TAG = "Typefaces"
  private final val cache = new util.Hashtable[String, Typeface]
}

object Shadowsocks {
  // Constants
  private final val TAG = "Shadowsocks"
  private final val REQUEST_CONNECT = 1
  private val EXECUTABLES = Array(Executable.PDNSD, Executable.REDSOCKS, Executable.SS_TUNNEL, Executable.SS_LOCAL,
    Executable.TUN2SOCKS)
}

class Shadowsocks extends AppCompatActivity with ServiceBoundContext {

  import Shadowsocks._

  // Variables
  var serviceStarted: Boolean = _
  var fab: FloatingActionButton = _
  var fabProgressCircle: FABProgressCircle = _
  var progressDialog: ProgressDialog = _
  var state = State.STOPPED
  var currentProfile = new Profile

  // Services
  private val callback = new IShadowsocksServiceCallback.Stub {
    def stateChanged(s: Int, profileName: String, m: String) {
      handler.post(() => {
        s match {
          case State.CONNECTING =>
            fab.setBackgroundTintList(greyTint)
            fab.setImageResource(R.drawable.ic_start_busy)
            fab.setEnabled(false)
            fabProgressCircle.show()
            preferences.setEnabled(false)
            stat.setVisibility(View.GONE)
          case State.CONNECTED =>
            fab.setBackgroundTintList(greenTint)
            if (state == State.CONNECTING) {
              fabProgressCircle.beginFinalAnimation()
              fabProgressCircle.postDelayed(hideCircle, 2800)
            } else {
              fabProgressCircle.postDelayed(hideCircle, 1000)
            }
            fab.setEnabled(true)
            changeSwitch(checked = true)
            preferences.setEnabled(false)
            stat.setVisibility(View.VISIBLE)
            if (app.isNatEnabled) connectionTestText.setVisibility(View.GONE)
            else {
              connectionTestText.setVisibility(View.VISIBLE)
              connectionTestText.setText(getString(R.string.connection_test_pending))
            }
            if (app.settings.getBoolean(Key.AUTO_TEST_CONNECTIVITY, true)) checkConnection(2, 3)
          case State.STOPPED =>
            fab.setBackgroundTintList(greyTint)
            fabProgressCircle.postDelayed(hideCircle, 1000)
            fab.setEnabled(true)
            changeSwitch(checked = false)
            if (m != null) {
              val snackbar = Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.vpn_error).formatLocal(Locale.ENGLISH, m), Snackbar.LENGTH_LONG)
              if (m == getString(R.string.nat_no_root)) snackbar.setAction(R.string.switch_to_vpn,
                (_ => preferences.natSwitch.setChecked(false)): View.OnClickListener)
              snackbar.show
              Log.e(TAG, "Error to start VPN service: " + m)
            }
            preferences.setEnabled(true)
            stat.setVisibility(View.GONE)
            // ignore test
            testCount += 1
          case State.STOPPING =>
            fab.setBackgroundTintList(greyTint)
            fab.setImageResource(R.drawable.ic_start_busy)
            fab.setEnabled(false)
            if (state == State.CONNECTED) fabProgressCircle.show() // ignore for stopped
            preferences.setEnabled(false)
            stat.setVisibility(View.GONE)
        }
        state = s
      })
    }

    def trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) {
      handler.post(() => updateTraffic(txRate, rxRate, txTotal, rxTotal))
    }
  }

  def updateTraffic(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) {
    txText.setText(TrafficMonitor.formatTraffic(txTotal))
    rxText.setText(TrafficMonitor.formatTraffic(rxTotal))
    txRateText.setText(TrafficMonitor.formatTraffic(txRate) + "/s")
    rxRateText.setText(TrafficMonitor.formatTraffic(rxRate) + "/s")
  }

  def attachService: Unit = attachService(callback)

  override def onServiceConnected() {
    // Update the UI
    if (fab != null) fab.setEnabled(true)
    updateState()
    if (Build.VERSION.SDK_INT >= 21 && app.isNatEnabled) {
      val snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.nat_deprecated, Snackbar.LENGTH_LONG)
      snackbar.setAction(R.string.switch_to_vpn, (_ => preferences.natSwitch.setChecked(false)): View.OnClickListener)
      snackbar.show
    }
  }

  override def onServiceDisconnected() {
    if (fab != null) fab.setEnabled(false)
  }


  override def binderDied {
    detachService()
    app.crashRecovery()
    attachService
  }

  private var testCount: Int = _
  private var stat: View = _
  private var connectionTestText: TextView = _
  private var txText: TextView = _
  private var rxText: TextView = _
  private var txRateText: TextView = _
  private var rxRateText: TextView = _

  private lazy val greyTint = ContextCompat.getColorStateList(this, R.color.material_blue_grey_700)
  private lazy val greenTint = ContextCompat.getColorStateList(this, R.color.material_green_700)
  //private var adView: AdView = _
  private lazy val preferences =
  getFragmentManager.findFragmentById(android.R.id.content).asInstanceOf[ShadowsocksSettings]
  private lazy val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

  val handler = new Handler()

  private def changeSwitch(checked: Boolean) {
    serviceStarted = checked
    fab.setImageResource(if (checked) R.drawable.ic_start_connected else R.drawable.ic_start_idle)
    if (fab.isEnabled) {
      fab.setEnabled(false)
      handler.postDelayed(() => fab.setEnabled(true), 1000)
    }
  }

  private def showProgress(msg: Int): Handler = {
    clearDialog()
    progressDialog = ProgressDialog.show(this, "", getString(msg), true, false)
    new Handler {
      override def handleMessage(msg: Message) {
        clearDialog()
      }
    }
  }

  def cancelStart() {
    clearDialog()
    changeSwitch(checked = false)
  }

  def prepareStartService() {
    Utils.ThrowableFuture {
      if (app.isNatEnabled) serviceLoad()
      else {
        val intent = VpnService.prepare(this)
        if (intent != null) {
          startActivityForResult(intent, REQUEST_CONNECT)
        } else {
          handler.post(() => onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null))
        }
      }
    }
  }
  
  def checkLatency(timeout: Int = 2, retry: Int = 1): Unit = {
    connectionTestText = findViewById(R.id.connection_test).asInstanceOf[TextView]
    val id = synchronized {
      testCount += 1
      handler.post(() => connectionTestText.setText(R.string.connection_test_testing))
      testCount
    }
    Future{
      if (Build.VERSION.SDK_INT < 21) {
        val elapsed = NetUtils.testConnectionStartup4("http://clients3.google.com/generate_204", timeout)
        getString(R.string.connection_test_available, elapsed: java.lang.Long)
      } else {
        val client = new OkHttpClient.Builder()
          .connectTimeout(timeout, TimeUnit.SECONDS)
          .writeTimeout(timeout, TimeUnit.SECONDS)
          .readTimeout(timeout, TimeUnit.SECONDS)
          .retryOnConnectionFailure(false)
          .build()
        val request = new Request.Builder()
          .url("http://clients3.google.com/generate_204")
          .build()
        if (testCount!=id) return
        client.newCall(request).execute().body().close()
        val start = SystemClock.elapsedRealtime()
        val response = client.newCall(request).execute()
        val elapsed = SystemClock.elapsedRealtime() - start
        if (!(response.code == 204 || response.code == 200)) {
          throw new Exception(getString(R.string.connection_test_error_status_code, response.code: Integer))
        }
        getString(R.string.connection_test_available, elapsed: java.lang.Long)
      }
    }
      .recover{
        case e => {
          e.printStackTrace()
//          Log.e(TAG, e.getMessage)
          getString(R.string.connection_test_fail)
        }
      }
      .filter(testCount == id && app.isVpnEnabled && serviceStarted && !TextUtils.isEmpty(_))
      .foreach(result => {
        handler.post(() => connectionTestText.setText(result))
      })
  }

  def checkConnection(timeout: Int, attempts: Int = 1): Unit = {
    connectionTestText = findViewById(R.id.connection_test).asInstanceOf[TextView]
    val id = synchronized {
      testCount += 1
//      handler.post(() => connectionTestText.setText(R.string.connection_test_testing))
      testCount
    }
    Utils.ThrowableFuture {
      if (testCount == id) {
        handler.post(() => {connectionTestText.setText(R.string.connection_test_testing)})
        val result = Retryer.exponentialBackoff[Long](attempts, 350)
            .on(i => NetUtils.testConnectionStartup("http://clients3.google.com/generate_204", timeout + i),
              SuccessConnect,
              e => {
                // e.printStackTrace()
                handler.post(() => connectionTestText.setText(getString(R.string.retry_test)))
                FailureConnect(getString(R.string.connection_test_unavailable))
              })
        synchronized(if (testCount == id && app.isVpnEnabled && serviceStarted) handler.post(() =>
          connectionTestText.setText(result.msg)
         ))
      }
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
//    getWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
//    getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.layout_main)
    // Initialize Toolbar
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    updateTitle(toolbar)
    toolbar.setTitleTextAppearance(toolbar.getContext, R.style.Toolbar_Logo)
    val field = classOf[Toolbar].getDeclaredField("mTitleTextView")
    field.setAccessible(true)
    val title = field.get(toolbar).asInstanceOf[TextView]
    title.setFocusable(true)
    title.setGravity(0x10)
    title.getLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
    title.setOnClickListener(_ => {
      val intent = new Intent(this, classOf[ProfileManagerActivity])
      val sortMethod = app.settings.getString(Key.SORT_METHOD, "default")
      if (sortMethod == Key.SORT_METHOD_ELAPSED) {
        intent.setAction(Action.SORT)
      }
      if (sortMethod == Key.SORT_METHOD_DOWNLOAD) {
        intent.setAction(Action.SORT_DOWNLOAD)
      }
      startActivity(intent)
    })
    val typedArray = obtainStyledAttributes(Array(R.attr.selectableItemBackgroundBorderless))
    title.setBackgroundResource(typedArray.getResourceId(0, 0))
    typedArray.recycle
    val tf = Typefaces.get(this, "fonts/Iceland.ttf")
    if (tf != null) title.setTypeface(tf)
    title.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0)

    stat = findViewById(R.id.stat)
    connectionTestText = findViewById(R.id.connection_test).asInstanceOf[TextView]
    txText = findViewById(R.id.tx).asInstanceOf[TextView]
    txRateText = findViewById(R.id.txRate).asInstanceOf[TextView]
    rxText = findViewById(R.id.rx).asInstanceOf[TextView]
    rxRateText = findViewById(R.id.rxRate).asInstanceOf[TextView]
    stat.setOnClickListener(_ => checkLatency())
    fab = findViewById(R.id.fab).asInstanceOf[FloatingActionButton]
    fabProgressCircle = findViewById(R.id.fabProgressCircle).asInstanceOf[FABProgressCircle]
    fab.setOnClickListener(_ => if (serviceStarted) serviceStop()
    else if (bgService != null) prepareStartService()
    else changeSwitch(checked = false))
    fab.setOnLongClickListener((v: View) => {
      Utils.positionToast(Toast.makeText(this, if (serviceStarted) R.string.stop else R.string.connect,
        Toast.LENGTH_SHORT), fab, getWindow, 0, Utils.dpToPx(this, 8)).show
      true
    })
    updateTraffic(0, 0, 0, 0)

//    app.ssrsubManager.getFirstSSRSub match {
//      case Some(first) => {
//
//      }
//      case None => app.ssrsubManager.createDefault()
//    }

    if (app.settings.getInt(Key.ssrsub_autoupdate, 0) == 1) {
      SSRSubUpdateJob.schedule()
    }

    handler.post(() => attachService)
  }

  private def hideCircle() {
    try {
      fabProgressCircle.hide()
    } catch {
      case _: java.lang.NullPointerException => // Ignore
    }
  }

  private def updateState(resetConnectionTest: Boolean = true) {
    if (bgService != null) {
      bgService.getState match {
        case State.CONNECTING =>
          fab.setBackgroundTintList(greyTint)
          serviceStarted = false
          fab.setImageResource(R.drawable.ic_start_busy)
          preferences.setEnabled(false)
          fabProgressCircle.show()
          stat.setVisibility(View.GONE)
        case State.CONNECTED =>
          fab.setBackgroundTintList(greenTint)
          serviceStarted = true
          fab.setImageResource(R.drawable.ic_start_connected)
          preferences.setEnabled(false)
          fabProgressCircle.postDelayed(hideCircle, 100)
          stat.setVisibility(View.VISIBLE)
          if (resetConnectionTest || state != State.CONNECTED)
            if (app.isNatEnabled) connectionTestText.setVisibility(View.GONE)
            else {
              connectionTestText.setVisibility(View.VISIBLE)
              connectionTestText.setText(getString(R.string.connection_test_pending))
            }
        case State.STOPPING =>
          fab.setBackgroundTintList(greyTint)
          serviceStarted = false
          fab.setImageResource(R.drawable.ic_start_busy)
          preferences.setEnabled(false)
          fabProgressCircle.show()
          stat.setVisibility(View.GONE)
        case _ =>
          fab.setBackgroundTintList(greyTint)
          serviceStarted = false
          fab.setImageResource(R.drawable.ic_start_idle)
          preferences.setEnabled(true)
          fabProgressCircle.postDelayed(hideCircle, 100)
          stat.setVisibility(View.GONE)
      }
      state = bgService.getState
    }
  }

  private def updateCurrentProfile(): Boolean = {
    // when subscription updated and the VPN has already connected then do nothing
    if (preferences.profile != null &&
      app.profileId != preferences.profile.id &&
      serviceStarted) {
      val existedProfile = app.profileManager.checkLastExistProfile(preferences.profile)
      if (existedProfile != null && existedProfile.id == app.profileId) {
        return false
      }
    }
    // Check if current profile changed
    if (preferences.profile == null || app.profileId != preferences.profile.id) {
      val profile = app.currentProfile match {
        case Some(profile) => Option(profile) // updated
        case None => // removed
          app.switchProfile((app.profileManager.getFirstProfile match {
            case Some(first) => first
            case None => {
              val defaultProfile = app.profileManager.createDefault()
              app.appStateManager.createDefault(defaultProfile.id)
              defaultProfile
            }
          }).id)
      }
      profile.foreach(updatePreferenceScreen)

      if (serviceStarted) serviceLoad()

      true
    } else {
      preferences.refreshProfile()
      false
    }
  }

  protected override def onResume() {
    super.onResume()

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    updateTitle(toolbar)

    app.refreshContainerHolder

    updateState(updateCurrentProfile())
  }

  private[this] def updateTitle (toolbar: Toolbar): Unit = {
    val isXray = app.settings.getString(Key.V2RAY_CORE, "core") == "xray";
    val title = app.currentProfile.map {
      case p if p.isV2Ray && isXray => "XRay"
      case p if p.isV2Ray && !isXray => "V2Ray"
      case p if p.isVless => "VLess"
      case p if p.isTrojan => "Trojan"
      case p if p.isShadowSocks => "Shadowsocks"
      case _ => "shadowsocks R"
    }.getOrElse("shadowsocks R")
    toolbar.setTitle(title)
  }

  private def updatePreferenceScreen(profile: Profile) {
    preferences.setProfile(profile)
  }

  override def onStart() {
    super.onStart()
    registerCallback
  }

  override def onStop() {
    super.onStop()
    unregisterCallback
    clearDialog()
  }

  private var _isDestroyed: Boolean = _

  override def isDestroyed = if (Build.VERSION.SDK_INT >= 17) super.isDestroyed else _isDestroyed

  override def onDestroy() {
    super.onDestroy()
    _isDestroyed = true
    detachService()
    new BackupManager(this).dataChanged()
    handler.removeCallbacksAndMessages(null)
  }

  def recovery() {
    if (serviceStarted) serviceStop()
    val h = showProgress(R.string.recovering)
    Utils.ThrowableFuture {
      app.copyAssets()
      h.sendEmptyMessage(0)
    }
  }

  def ignoreBatteryOptimization() {
    // http://blog.csdn.net/laxian2009/article/details/52474214

    var exception = false
    try {
      val powerManager: PowerManager = this.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
      val packageName = this.getPackageName
      val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
      if (!hasIgnored) {
        val intent = new Intent()
        intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.setData(android.net.Uri.parse("package:" + packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
      } else {
        startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
      }
      exception = false
    } catch {
      case _: Throwable =>
        exception = true
    } finally {
    }
    if (exception) {
      try {
        val intent = new Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val cn = new ComponentName(
          "com.android.settings",
          "com.android.com.settings.Settings@HighPowerApplicationsActivity"
        )

        intent.setComponent(cn)
        startActivity(intent)

        exception = false
      } catch {
        case _: Throwable =>
          exception = true
      } finally {
      }
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = resultCode match {
    case Activity.RESULT_OK =>
      serviceLoad()
    case _ =>
      cancelStart()
      Log.e(TAG, "Failed to start VpnService")
  }

  def serviceStop() {
    if (bgService != null) {
      bgService.use(-1)
      notificationManager.cancel(1)
    }
  }

  /** Called when connect button is clicked. */
  def serviceLoad() {
    bgService.use(app.profileId)

    if (app.isVpnEnabled) {
      changeSwitch(checked = false)
    }
  }

  def clearDialog() {
    if (progressDialog != null && progressDialog.isShowing) {
      if (!isDestroyed) progressDialog.dismiss()
      progressDialog = null
    }
  }
}
