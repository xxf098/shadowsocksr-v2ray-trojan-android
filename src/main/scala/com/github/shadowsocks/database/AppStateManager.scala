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
      dbHelper.appStateDao.queryForId(1) match {
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
      val query = dbHelper.appStateDao.queryBuilder().selectColumns("per_app_proxy_enable").where().eq("id", 1)
      val result = dbHelper.appStateDao.query(query.prepare())
      if (result.size() == 1) result.get(0).per_app_proxy_enable else false
    } catch {
      case ex: Exception =>
        app.track(ex)
        false
    }
  }

  def getProfileID (): Int = {
    try {
      val query = dbHelper.appStateDao.queryBuilder().selectColumns("profile_id").where().eq("id", 1)
      val result = dbHelper.appStateDao.query(query.prepare())
      if (result.size() == 1) result.get(0).profile_id else -1
    } catch {
      case ex: Exception =>
        app.track(ex)
        -1
    }
  }

  def saveProfileId (profileId: Int): Unit = {
      try {
        dbHelper.appStateDao.executeRawNoArgs(s"UPDATE `appstate` SET profile_id = '$profileId' WHERE id = 1")
      } catch {
        case e: Exception =>
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

  def saveAppState(profileId: Option[Int], perAppProxyEnabled: Option[Boolean], isBypassMode: Option[Boolean], packageNames: Option[String]): Unit = {
    try {
      dbHelper.appStateDao.queryForId(1) match {
        case appState: AppState => {
          appState.id = 1
          profileId.foreach(profile_id => appState.profile_id = profile_id)
          perAppProxyEnabled.foreach(per_app_proxy_enable => appState.per_app_proxy_enable = per_app_proxy_enable)
          isBypassMode.foreach(bypass_mode => appState.bypass_mode = bypass_mode)
          packageNames.foreach(package_names => appState.package_names = package_names)
          dbHelper.appStateDao.update(appState)
        }
        case _ => {
          val appState = new AppState {
            profile_id = profileId.getOrElse(-1)
            per_app_proxy_enable = perAppProxyEnabled.getOrElse(false)
            bypass_mode = isBypassMode.getOrElse(false)
            package_names = packageNames.getOrElse("")
          }
          dbHelper.appStateDao.create(appState)
        }
      }
    } catch {
      case ex: Exception =>
        app.track(ex)
        None
    }
  }

  def saveAppStateAsync(profileId: Option[Int], perAppProxyEnabled: Option[Boolean], isBypassMode: Option[Boolean], packageNames: Option[String]): Unit = {
    Future{
      saveAppState(profileId, perAppProxyEnabled, isBypassMode, packageNames)
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

}
