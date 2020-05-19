/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2013 <max.c.lv@gmail.com>
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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import com.github.shadowsocks.ShadowsocksApplication.app

import scala.collection.JavaConverters._
import scala.collection.mutable

object DBHelper {
  final val PROFILE = "profile.db"
  private var apps: mutable.Buffer[ApplicationInfo] = _

  def isAllDigits(x: String) = !x.isEmpty && (x forall Character.isDigit)

  def updateProxiedApps(context: Context, old: String) = {
    synchronized(if (apps == null) apps = context.getPackageManager.getInstalledApplications(0).asScala)
    val uidSet = old.split('|').filter(isAllDigits).map(_.toInt).toSet
    apps.filter(ai => uidSet.contains(ai.uid)).map(_.packageName).mkString("\n")
  }
}

class DBHelper(val context: Context)
  extends OrmLiteSqliteOpenHelper(context, DBHelper.PROFILE, null, 33) {
  import DBHelper._

  lazy val profileDao: Dao[Profile, Int] = getDao(classOf[Profile])
  lazy val ssrsubDao: Dao[SSRSub, Int] = getDao(classOf[SSRSub])
  lazy val appStateDao: Dao[AppState, Int] = getDao(classOf[AppState])

  def onCreate(database: SQLiteDatabase, connectionSource: ConnectionSource) {
    TableUtils.createTable(connectionSource, classOf[Profile])
    TableUtils.createTable(connectionSource, classOf[SSRSub])
    TableUtils.createTable(connectionSource, classOf[AppState])
  }

  def onUpgrade(database: SQLiteDatabase, connectionSource: ConnectionSource, oldVersion: Int,
    newVersion: Int) {
    if (oldVersion != newVersion) {
      if (oldVersion < 7) {
        profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';")
        onCreate(database, connectionSource)
        return
      }

      try {
        if (oldVersion < 8) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN udpdns SMALLINT;")
        }
        if (oldVersion < 9) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN route VARCHAR DEFAULT 'all';")
        } else if (oldVersion < 19) {
          profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'all' WHERE route IS NULL;")
        }
        if (oldVersion < 10) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN auth SMALLINT;")
        }
        if (oldVersion < 11) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ipv6 SMALLINT;")
        }
        if (oldVersion < 12) {
          profileDao.executeRawNoArgs("BEGIN TRANSACTION;")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;")
          TableUtils.createTable(connectionSource, classOf[Profile])
          profileDao.executeRawNoArgs(
            "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," +
              " udpdns, auth, ipv6, individual) " +
            "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, auth," +
            " ipv6, individual FROM `tmp`;")
          profileDao.executeRawNoArgs("DROP TABLE `tmp`;")
          profileDao.executeRawNoArgs("COMMIT;")
        } else if (oldVersion < 13) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;")
        }

        if (oldVersion < 15) {
          if (oldVersion >= 12) profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;")
          var i = 0
          for (profile <- profileDao.queryForAll.asScala) {
            if (oldVersion < 14) profile.individual = updateProxiedApps(context, profile.individual)
            profile.userOrder = i
            profileDao.update(profile)
            i += 1
          }
        }


        if (oldVersion < 16) {
          profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'bypass-lan-china' WHERE route = 'bypass-china'")
        }

        if (oldVersion < 19) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN dns VARCHAR DEFAULT '1.1.1.1:53';")
        }

        if (oldVersion < 20) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN china_dns VARCHAR DEFAULT '223.5.5.5:53,119.29.29.29:53';")
        }

        if (oldVersion < 21) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN protocol_param VARCHAR DEFAULT '';")
        }

        if (oldVersion < 22) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN elapsed LONG DEFAULT 0;")
        }

        if (oldVersion < 23) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN url_group VARCHAR DEFAULT '';")
        }

        if (oldVersion < 24) {
          TableUtils.createTable(connectionSource, classOf[SSRSub])
        }
        if (oldVersion < 25) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN proxy_protocol VARCHAR DEFAULT 'ssr';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_v VARCHAR DEFAULT '2';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_ps VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_add VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_port VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_id VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_aid VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_net VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_type VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_host VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_path VARCHAR DEFAULT '';")
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_tls VARCHAR DEFAULT '';")
        }
        if (oldVersion < 27) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_json_config VARCHAR DEFAULT '';")
        }
        if (oldVersion < 28) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ssrsub_id INTEGER DEFAULT '';")
        }

        if (oldVersion < 29) {
          profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN v_security VARCHAR DEFAULT '';")
        }
        if (oldVersion < 30) {
          TableUtils.createTableIfNotExists(connectionSource, classOf[AppState])
        }
        if (oldVersion < 31) {
          appStateDao.queryBuilder().selectColumns("id").queryForFirst() match {
            case _: AppState =>
            case _ => {
              val appState = new AppState {
                profile_id = -1
                per_app_proxy_enable = false
                bypass_mode = false
                package_names = ""
              }
              appStateDao.create(appState)
            }
          }
        }
        if (oldVersion < 32) {
          profileDao.executeRawNoArgs("ALTER TABLE `appstate` ADD COLUMN dns_nocache VARCHAR DEFAULT 'off';")
        }
        if (oldVersion < 33) {
          profileDao.executeRawNoArgs("ALTER TABLE `ssrsub` ADD COLUMN updated_at VARCHAR DEFAULT '';")
        }
      } catch {
        case ex: Exception =>
          app.track(ex)
          profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';")
          onCreate(database, connectionSource)
          return
      }
    }
  }
}
