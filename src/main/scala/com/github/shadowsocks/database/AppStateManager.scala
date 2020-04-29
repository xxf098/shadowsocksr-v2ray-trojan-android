package com.github.shadowsocks.database

import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.SSRSubManager.TAG
import com.github.shadowsocks.utils.Utils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AppStateManager(dbHelper: DBHelper) {

  def getAppState (): Option[AppState] = {
    try {
        dbHelper.appStateDao.queryBuilder().queryForFirst() match {
        case appState: AppState => Option(appState)
        case _ => None
      }
    } catch {
      case ex: Exception =>
        app.track(ex)
        None
    }
  }

  def getPerAppProxyEnable (): Boolean = {
    try {
      val result = dbHelper.appStateDao.queryBuilder().selectColumns("per_app_proxy_enable").queryForFirst()
      if (result != null) result.per_app_proxy_enable else false
    } catch {
      case ex: Exception =>
        app.track(ex)
        false
    }
  }

  def getProfileID (): Int = {
    try {
      val result = dbHelper.appStateDao.queryBuilder().selectColumns("profile_id").queryForFirst()
      if (result != null) result.profile_id else -1
    } catch {
      case ex: Exception =>
        app.track(ex)
        -1
    }
  }

  def saveProfileId (profileId: Int): Unit = {
      try {
        dbHelper.appStateDao.executeRawNoArgs(s"UPDATE appstate SET profile_id = '$profileId'")
      } catch {
        case e: Exception =>
          Log.e("====", e.getMessage)
          e.printStackTrace()
      }
  }

  def saveProfileIdAsync (profileId: Int): Unit = {
    Utils.ThrowableFuture {
      saveProfileId(profileId)
    }
  }

//  def getColumn (): Option[AppState] = {
//
//  }

  def saveAppState(profileId: Option[Int],
                   perAppProxyEnabled: Option[Boolean],
                   isBypassMode: Option[Boolean],
                   packageNames: Option[String],
                  dnsNoCache: Option[String] = None): Option[AppState] = {
    try {
      dbHelper.appStateDao.queryBuilder().queryForFirst() match {
        case appState: AppState => {
          appState.id = appState.id
          profileId.foreach(profile_id => appState.profile_id = profile_id)
          perAppProxyEnabled.foreach(per_app_proxy_enable => appState.per_app_proxy_enable = per_app_proxy_enable)
          isBypassMode.foreach(bypass_mode => appState.bypass_mode = bypass_mode)
          packageNames.foreach(package_names => appState.package_names = package_names)
          dnsNoCache.foreach(dns_nocache => appState.dns_nocache = dns_nocache)
          dbHelper.appStateDao.update(appState)
          Some(appState)
        }
        case _ => {
          val appState = new AppState {
            profile_id = profileId.getOrElse(-1)
            per_app_proxy_enable = perAppProxyEnabled.getOrElse(false)
            bypass_mode = isBypassMode.getOrElse(false)
            package_names = packageNames.getOrElse("")
            dns_nocache = dnsNoCache.getOrElse("off")
          }
          dbHelper.appStateDao.create(appState)
          Some(appState)
        }
      }
    } catch {
      case ex: Exception =>
        app.track(ex)
        None
    }
  }

  def saveAppStateAsync(profileId: Option[Int],
                        perAppProxyEnabled: Option[Boolean],
                        isBypassMode: Option[Boolean],
                        packageNames: Option[String],
                        dnsNoCache: Option[String] = None): Unit = {
    Future{
      saveAppState(profileId, perAppProxyEnabled, isBypassMode, packageNames, dnsNoCache)
    }
  }

    def saveIsBypassMode(isBypassMode: Boolean): Unit = {
    saveAppState(None, None, Some(isBypassMode), None)
  }

  def savePerAppProxyEnabled(enabled: Boolean): Unit = {
    saveAppState(None, Some(enabled), None, None)
  }

  def savePackageNames(packageNames: String): Unit = {
    saveAppStateAsync(None, None, None, Some(packageNames))
  }

  def saveDNSNoCache(nocache: String): Unit = {
    val dnsNoCache = if(nocache == "on") "on" else "off"
    saveAppStateAsync(None, None, None, None, Option(dnsNoCache))
  }

  def createDefault(profileId: Int): AppState = {
    saveAppState(Some(profileId), None, None, None).getOrElse(new AppState())
  }

}
