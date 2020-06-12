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

import java.util.concurrent.atomic.AtomicBoolean

import android.Manifest.permission
import android.app.TaskStackBuilder
import android.content._
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.nfc.Tag
import android.os.{Bundle, Handler}
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar.OnMenuItemClickListener
import android.support.v7.widget.{DefaultItemAnimator, DividerItemDecoration, LinearLayoutManager, RecyclerView, Toolbar}
import android.util.Log
import android.view._
import android.widget._
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.CloseUtils.autoClose
import com.github.shadowsocks.utils.{Key, Utils}
import android.support.v7.widget.SearchView


import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions

// TODO: Apply setting to all config
object AppManager {
  val TAG = "AppManager"
  case class ProxiedApp(name: String, packageName: String, icon: Drawable)
  private case class ListEntry(switch: Switch, text: TextView, icon: ImageView)

  private var instance: AppManager = _

  private var receiverRegistered: Boolean = _
  private var cachedApps: Array[ProxiedApp] = _
  private def getApps(pm: PackageManager) = {
    if (!receiverRegistered) {
      val filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED)
      filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
      filter.addDataScheme("package")
      app.registerReceiver((context: Context, intent: Intent) =>
        if (intent.getAction != Intent.ACTION_PACKAGE_REMOVED ||
          !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
          synchronized(cachedApps = null)
          val instance = AppManager.instance
          if (instance != null) instance.reloadApps()
        }, filter)
      receiverRegistered = true
    }
    synchronized {
      if (cachedApps == null) cachedApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        .filter(p => p.requestedPermissions != null && p.requestedPermissions.contains(permission.INTERNET) && p.packageName != app.getPackageName)
        .map(p => ProxiedApp(pm.getApplicationLabel(p.applicationInfo).toString, p.packageName,
          p.applicationInfo.loadIcon(pm))).toArray
      cachedApps
    }
  }
}

class AppManager extends AppCompatActivity with OnMenuItemClickListener {
  import AppManager._

  private final class AppViewHolder(val view: View) extends RecyclerView.ViewHolder(view) with View.OnClickListener {
    private val icon = itemView.findViewById(R.id.icon).asInstanceOf[ImageView]
    private val tvName = itemView.findViewById(R.id.name).asInstanceOf[TextView]
    private val tvPackageName = itemView.findViewById(R.id.packagename).asInstanceOf[TextView]
    private val checkbox = itemView.findViewById(R.id.checkbox).asInstanceOf[CheckBox]
    private var item: ProxiedApp = _
    itemView.setOnClickListener(this)

    private def proxied = proxiedApps.contains(item.packageName)

    def bind(app: ProxiedApp) {
      this.item = app
      icon.setImageDrawable(app.icon)
      tvName.setText(app.name)
      tvPackageName.setText(app.packageName)
      checkbox.setChecked(proxied)
    }

    def onClick(v: View) {
      if (proxied) {
        proxiedApps.remove(item.packageName)
        checkbox.setChecked(false)
      } else {
        proxiedApps.add(item.packageName)
        checkbox.setChecked(true)
      }
    }
  }

  private final class AppsAdapter extends RecyclerView.Adapter[AppViewHolder] {
    private lazy val appsCache = getApps(getPackageManager).sortWith((a, b) => {
      val aProxied = proxiedApps.contains(a.packageName)
      if (aProxied ^ proxiedApps.contains(b.packageName)) aProxied else a.name.compareToIgnoreCase(b.name) < 0
    })
    private var apps = appsCache

    lazy val packageNames = apps.map(_.packageName)

    def getItemCount = apps.length
    def onBindViewHolder(vh: AppViewHolder, i: Int) = vh.bind(apps(i))
    def onCreateViewHolder(vg: ViewGroup, i: Int) =
      new AppViewHolder(LayoutInflater.from(vg.getContext).inflate(R.layout.layout_apps_item, vg, false))

    // TODO: parallelStream
    def applyFilter(keyword: String) = {
      apps = if(keyword.length == 0) appsCache
      else apps.filter(a => a.name.toLowerCase.contains(keyword) || a.packageName.toLowerCase.contains(keyword))
      notifyDataSetChanged()
    }
  }

  private var proxiedApps: mutable.HashSet[String] = _
  private var toolbar: Toolbar = _
  private var bypassSwitch: Switch = _
  private var appListView: RecyclerView = _
  private var loadingView: View = _
  private val appsLoading = new AtomicBoolean
  private var handler: Handler = _
  private val profile = app.currentProfile.orNull
  private lazy val appState = app.appStateManager.getAppState()

  private def initProxiedApps(str: String = "") = proxiedApps = str.split('\n').to[mutable.HashSet]

  override def onDestroy() {
    instance = null
    super.onDestroy()
    if (handler != null) {
      handler.removeCallbacksAndMessages(null)
      handler = null
    }
  }

  def onMenuItemClick(item: MenuItem): Boolean = {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    item.getItemId match {
      case R.id.action_apply_all =>
//        app.profileManager.getAllProfiles match {
//          case Some(profiles) =>
//            val proxiedAppString = profile.individual
//            profiles.foreach(p => {
//              p.individual = proxiedAppString
//              app.profileManager.updateProfile(p)
//            })
//            Toast.makeText(this, R.string.action_apply_all, Toast.LENGTH_SHORT).show
//          case _ => Toast.makeText(this, R.string.action_export_err, Toast.LENGTH_SHORT).show
//        }
        return true
      case R.id.action_export =>
        val bypass = bypassSwitch.isChecked
        val proxiedAppString = proxiedApps.mkString("\n")
        val clip = ClipData.newPlainText(Key.perAppProxy, bypass + "\n" + proxiedAppString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.action_export_msg, Toast.LENGTH_SHORT).show()
        return true
      case R.id.action_import =>
        if (clipboard.hasPrimaryClip) {
          val proxiedAppSequence = clipboard.getPrimaryClip.getItemAt(0).getText
          if (proxiedAppSequence != null) {
            val proxiedAppString = proxiedAppSequence.toString
            if (!proxiedAppString.isEmpty) {
              val i = proxiedAppString.indexOf('\n')
              try {
                val (enabled, apps) = if (i < 0) (proxiedAppString, "")
                  else (proxiedAppString.substring(0, i), proxiedAppString.substring(i + 1))
                bypassSwitch.setChecked(enabled.toBoolean)
                Toast.makeText(this, R.string.action_import_msg, Toast.LENGTH_SHORT).show()
                appListView.setVisibility(View.GONE)
                loadingView.setVisibility(View.VISIBLE)
                initProxiedApps(apps)
                reloadAppsWithoutInit()
                return true
              } catch {
                case _: IllegalArgumentException =>
                  Toast.makeText(this, R.string.action_import_err, Toast.LENGTH_SHORT).show
              }
            }
          }
        }
        Toast.makeText(this, R.string.action_import_err, Toast.LENGTH_SHORT).show()
        return true
      case R.id.select_all => {
        val appsAdapter = appListView.getAdapter.asInstanceOf[AppsAdapter]
        if (proxiedApps.size >= appsAdapter.getItemCount) {
          proxiedApps.clear()
        } else {
          appsAdapter.packageNames.foreach(item => proxiedApps.add(item))
        }
        appsAdapter.notifyDataSetChanged()
        return true
      }
      case R.id.select_proxy_app => {
        Utils.ThrowableFuture(autoClose(getAssets.open("proxy_apps.txt"))(in => {
          val defaultProxyApps = scala.io.Source.fromInputStream(in).getLines().toList
//          Log.e("===", s"defaultProxyApps${defaultProxyApps.size}")
          proxiedApps.clear()
          val appsAdapter = appListView.getAdapter.asInstanceOf[AppsAdapter]
          appsAdapter.packageNames
            .filter(item => {
              val index = defaultProxyApps.indexOf(item)
              if (bypassSwitch.isChecked) index < 0 else index >= 0
            })
            .foreach(proxiedApps.add(_))
//          Log.e("===", s"proxiedApps${proxiedApps.size}")
          reloadAppsWithoutInit()
        }))
        return true
      }
      case _ => return false
    }
    false
  }

  protected override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    if (profile == null) finish()

    handler = new Handler()

    this.setContentView(R.layout.layout_apps)
    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.proxied_apps)
    toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
    toolbar.setNavigationOnClickListener(_ => {
      val intent = getParentActivityIntent
      if (shouldUpRecreateTask(intent) || isTaskRoot)
        TaskStackBuilder.create(this).addNextIntentWithParentStack(intent).startActivities()
      else finish()
    })
    toolbar.inflateMenu(R.menu.app_manager_menu)
    toolbar.setOnMenuItemClickListener(this)


    findViewById(R.id.onSwitch).asInstanceOf[Switch]
      .setOnCheckedChangeListener((_, checked) => {
        app.appStateManager.savePerAppProxyEnabled(checked)
        finish()
      })

    bypassSwitch = findViewById(R.id.bypassSwitch).asInstanceOf[Switch]
    bypassSwitch.setChecked(appState.map(_.bypass_mode).getOrElse(false))
    bypassSwitch.setOnCheckedChangeListener((_, checked) => {
      app.appStateManager.saveIsBypassMode(checked)
    })

    loadingView = findViewById(R.id.loading)
    appListView = findViewById(R.id.applistview).asInstanceOf[RecyclerView]
    val layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false)
    appListView.setLayoutManager(layoutManager)
    appListView.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation))
    appListView.setItemAnimator(new DefaultItemAnimator)

    instance = this
    loadAppsAsync()
    // search
    val searchView = findViewById(R.id.action_search).asInstanceOf[SearchView]
    val searchTextView = searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text).asInstanceOf[AutoCompleteTextView]
    try {
      val mCursorDrawableRes = classOf[TextView].getDeclaredField("mCursorDrawableRes")
      mCursorDrawableRes.setAccessible(true)
      mCursorDrawableRes.set(searchTextView, R.drawable.cursor)
    } catch{
      case e: Exception =>
    }
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener {
      override def onQueryTextSubmit(s: String): Boolean = false

      override def onQueryTextChange(s: String): Boolean = {
        val adapter = appListView.getAdapter.asInstanceOf[AppsAdapter]
        if (adapter != null) {
          adapter.applyFilter(s.toLowerCase())
        }
        true
      }
    })
  }


  override def onPause(): Unit = {
    super.onPause()
    app.appStateManager.savePackageNames(proxiedApps.mkString("\n"))
  }

  def reloadApps() = if (!appsLoading.compareAndSet(true, false)) loadAppsAsync()
  def reloadAppsWithoutInit() = if (!appsLoading.compareAndSet(true, false)) loadAppsAsync(false)
  def loadAppsAsync(needInitProxiedApps: Boolean = true) {
    if (!appsLoading.compareAndSet(false, true)) return
    Utils.ThrowableFuture {
      var adapter: AppsAdapter = null
      if (needInitProxiedApps) initProxiedApps(appState.map(_.package_names).getOrElse(""))
      app.appStateManager.savePerAppProxyEnabled(true)
//      Log.e("====", s"proxiedApps.size: ${Option(proxiedApps).map(_.size).getOrElse("")}")
      do {
        appsLoading.set(true)
        adapter = new AppsAdapter
      } while (!appsLoading.compareAndSet(true, false))
      handler.post(() => {
        appListView.setAdapter(adapter)
        Utils.crossFade(AppManager.this, loadingView, appListView)
      })
    }
  }

  override def onKeyUp(keyCode: Int, event: KeyEvent) = keyCode match {
    case KeyEvent.KEYCODE_MENU =>
      if (toolbar.isOverflowMenuShowing) toolbar.hideOverflowMenu else toolbar.showOverflowMenu
    case _ => super.onKeyUp(keyCode, event)
  }
}
