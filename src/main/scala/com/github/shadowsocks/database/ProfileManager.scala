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

package com.github.shadowsocks.database

import android.util.Log
import com.github.shadowsocks.ShadowsocksApplication.app

object ProfileManager {
  private final val TAG = "ProfileManager"
}

class ProfileManager(dbHelper: DBHelper) {
  import ProfileManager._

  var profileAddedListener: Profile => Any = _
  def setProfileAddedListener(listener: Profile => Any) = this.profileAddedListener = listener

  def createProfile(p: Profile = null): Profile = {
    // if (p=null) return new Profile()
    val profile = if (p == null) new Profile else p
    profile.id = 0
    profile.url_group = if (profile.url_group.isEmpty) "Default Group"  else profile.url_group
    try {
      app.currentProfile match {
        case Some(oldProfile) =>
          // Copy Feature Settings from old profile
          profile.route = oldProfile.route
          profile.ipv6 = oldProfile.ipv6
//          profile.proxyApps = oldProfile.proxyApps
//          profile.bypass = oldProfile.bypass
//          profile.individual = oldProfile.individual
          profile.udpdns = oldProfile.udpdns
        case _ =>
      }
      val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder.selectRaw("MAX(userOrder)")
        .prepareStatementString).getFirstResult
      if (last != null && last.length == 1 && last(0) != null) profile.userOrder = last(0).toInt + 1
      dbHelper.profileDao.createOrUpdate(profile)
      if (profileAddedListener != null) profileAddedListener(profile)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "addProfile", ex)
        app.track(ex)
    }
    profile
  }

  def createProfile_dr(p: Profile = null): Profile = {
    val profile = if (p == null) new Profile else p
    profile.id = 0
    try {
      app.currentProfile match {
        case Some(oldProfile) =>
          // Copy Feature Settings from old profile
          profile.route = oldProfile.route
          profile.ipv6 = oldProfile.ipv6
//          profile.proxyApps = oldProfile.proxyApps
//          profile.bypass = oldProfile.bypass
//          profile.individual = oldProfile.individual
          profile.udpdns = oldProfile.udpdns
          profile.dns = oldProfile.dns
          profile.china_dns = oldProfile.china_dns
        case _ =>
      }
      val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder.selectRaw("MAX(userOrder)")
        .prepareStatementString).getFirstResult
      if (last != null && last.length == 1 && last(0) != null) profile.userOrder = last(0).toInt + 1

      val last_exist = checkLastExistProfile(profile)
      if (last_exist == null)
      {
        dbHelper.profileDao.createOrUpdate(profile)
        if (profileAddedListener != null) profileAddedListener(profile)
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "addProfile", ex)
        app.track(ex)
    }
    profile
  }

  // TODO: notify adapter data batch change
  def createProfile_sub(p: Profile = null): Int = {
    val profile = if (p == null) new Profile else p
    profile.id = 0
    try {
      app.currentProfile match {
        case Some(oldProfile) =>
          // Copy Feature Settings from old profile
          profile.route = oldProfile.route
          profile.ipv6 = oldProfile.ipv6
//          profile.proxyApps = oldProfile.proxyApps
//          profile.bypass = oldProfile.bypass
//          profile.individual = oldProfile.individual
          profile.udpdns = oldProfile.udpdns
          profile.dns = oldProfile.dns
          profile.china_dns = oldProfile.china_dns
        case _ =>
      }
      val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder.selectRaw("MAX(userOrder)")
        .prepareStatementString).getFirstResult
      if (last != null && last.length == 1 && last(0) != null) profile.userOrder = last(0).toInt + 1

      val last_exist = checkLastExistProfile(profile)
      if (last_exist != null) {
        profile.tx = last_exist.tx
        profile.rx = last_exist.rx
        profile.elapsed = last_exist.elapsed
      }
//      if (last_exist == null) {
//        dbHelper.profileDao.createOrUpdate(profile)
//        if (profileAddedListener != null)  {
//          profileAddedListener(profile)
//        }
//        0
//      } else {
//        last_exist.id
//      }
      // https://github.com/xxf098/shadowsocksr-v2ray-android/issues/49
      dbHelper.profileDao.createOrUpdate(profile)
      if (profileAddedListener != null) { profileAddedListener(profile) }
      0
    } catch {
      case ex: Exception =>
        Log.e(TAG, "addProfile", ex)
        app.track(ex)
        0
    }
  }

  def checkLastExistProfile(profile: Profile): Profile = {
    if (profile.isVmess) {
      dbHelper.profileDao.queryBuilder()
        .where().eq("v_add", profile.v_add)
        .and().eq("v_port", profile.v_port)
        .and().eq("v_id", profile.v_id)
        .and().eq("v_aid", profile.v_aid)
        .and().eq("v_net", profile.v_net)
        .and().eq("v_type", profile.v_type)
        .and().eq("v_host", profile.v_host)
        .and().eq("v_path", Option(profile.v_path).getOrElse(""))
        .and().eq("url_group", profile.url_group)
        .and().eq("v_ps", profile.v_ps)
        .and().eq("v_tls", profile.v_tls).queryForFirst()
    } else if (profile.isTrojan) {
      dbHelper.profileDao.queryBuilder()
        .where().eq("t_addr", profile.t_addr)
        .and().eq("t_port", profile.t_port)
        .and().eq("t_password", profile.t_password)
        .queryForFirst()
    } else {
      dbHelper.profileDao.queryBuilder()
        .where().eq("name", profile.name)
        .and().eq("host", profile.host)
        .and().eq("remotePort", profile.remotePort)
        .and().eq("password", profile.password)
        .and().eq("protocol", profile.protocol)
        .and().eq("protocol_param", profile.protocol_param)
        .and().eq("obfs", profile.obfs)
        .and().eq("obfs_param", profile.obfs_param)
        .and().eq("url_group", profile.url_group)
        .and().eq("method", profile.method).queryForFirst().asInstanceOf[Profile]
    }
  }

  def updateProfile(profile: Profile): Boolean = {
    try {
      dbHelper.profileDao.update(profile)
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfile", ex)
        app.track(ex)
        false
    }
  }

  def updateAllProfile_String(key:String, value:String): Boolean = {
    try {
      dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '" + value + "';")
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfile", ex)
        app.track(ex)
        false
    }
  }

  def updateAllProfileRoute(profileType:String, value:String): Boolean = {
    try {
      if (profileType == "v2ray_trojan") {
        dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET route" + " = '" + value + "' where coalesce(v_add, '') != '' or coalesce(t_addr, '') != '';")
      } else {
        dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET route" + " = '" + value + "' where coalesce(v_add, t_addr, '') = '';")
      }
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfileRoute", ex)
        app.track(ex)
        false
    }
  }

  def updateAllProfile_Boolean(key:String, value:Boolean): Boolean = {
    try {
      if (value) {
        dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '1';")
      } else {
        dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET " + key + " = '0';")
      }
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfile", ex)
        app.track(ex)
        false
    }
  }

  def updateGroupName(groupName:String, ssrsub_id:Int): Boolean = {
    try {
      dbHelper.profileDao.executeRawNoArgs(s"UPDATE `profile` SET url_group = '$groupName' WHERE ssrsub_id = $ssrsub_id")
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfile", ex)
        app.track(ex)
        false
    }
  }


  def getProfile(id: Int): Option[Profile] = {
    try {
      dbHelper.profileDao.queryForId(id) match {
        case profile: Profile => Option(profile)
        case _ => None
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getProfile", ex)
        app.track(ex)
        None
    }
  }

  def getProfileElapsed(ids: List[Int]): Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder
        .selectColumns("id", "elapsed")
        .where().in("id", ids.mkString(", ")).prepare)
        .toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getProfileElapsed", ex)
        None
    }
  }

  def delProfile(id: Int): Boolean = {
    try {
      dbHelper.profileDao.deleteById(id)
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "delProfile", ex)
        app.track(ex)
        false
    }
  }

  def getFirstProfile = {
    try {
      val result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.limit(1L).prepare)
      if (result.size == 1) Option(result.get(0)) else None
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfiles: Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("userOrder", true).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesByGroup(group:String): Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.where().eq("url_group", group).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesBySSRSub(ssrsub: SSRSub, compareUrlGroup: Boolean = false): Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      if (ssrsub.id > 0) {
        if (compareUrlGroup) {
          // prevent delete regrouped profiles
          Option(dbHelper.profileDao.query(
            dbHelper.profileDao.queryBuilder.where()
              .eq("ssrsub_id", ssrsub.id).and()
              .eq("url_group", ssrsub.url_group).prepare).toList)
        } else {
          Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.where().eq("ssrsub_id", ssrsub.id).prepare).toList)
        }
      } else {
        getAllProfilesByGroup(ssrsub.url_group)
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getFirstProfileBySSRSub(ssrsub: SSRSub): Option[Profile] = {
    try {
      import scala.collection.JavaConversions._
      val result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.limit(1L).where().eq("ssrsub_id", ssrsub.id).prepare).toList
      if (result.size == 1) Option(result.head) else None
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesByElapsed: Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("elapsed", true).where().not().eq("elapsed", 0).prepare).toList
      ++ dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("elapsed", true).where().eq("elapsed", 0).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesByDownload: Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("download_speed", false).where().not().eq("download_speed", 0).prepare).toList
        ++ dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("download_speed", false).where().eq("download_speed", 0).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesByGroupOrderByElapse(group: String): Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("elapsed", true).where().eq("url_group", group).and().not().eq("elapsed", 0).prepare).toList
        ++ dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("elapsed", true).where().eq("url_group", group).and().eq("elapsed", 0).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfilesByGroupOrderByDownload(group: String): Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("download_speed", false).where().eq("url_group", group).and().not().eq("download_speed", 0).prepare).toList
        ++ dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("download_speed", false).where().eq("url_group", group).and().eq("download_speed", 0).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def countAllProfilesByGroup (groupName: Option[String]): Long = {
    try {
      groupName match {
        case Some(name) => dbHelper.profileDao.queryBuilder().where().eq("url_group", name).countOf()
        case None => dbHelper.profileDao.countOf()
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        0L
    }
  }

  // find all url_group
  def getGroupNames: Option[List[String]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder()
        .selectColumns("url_group")
        .distinct()
        .prepare()).map(p => p.url_group)
        .toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def createDefault(): Profile = {
    val profile = new Profile {
      name = "项目地址: https://github.com/xxf098"
      host = "1.2.3.4"
      remotePort = 1234
      password = "ssrray"
      protocol = "auth_chain_a"
      obfs = "http_simple"
      method = "none"
      url_group = "Default Group"
    }
    createProfile(profile)
  }
}
