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

import java.net.{HttpURLConnection, URL, URLEncoder}
import java.util.Locale
import java.util.concurrent.TimeUnit

import android.os.Build
import android.text.TextUtils
import android.util.{Base64, Log}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.CloseUtils.autoClose
import com.github.shadowsocks.utils.{Parser, Utils}
import com.j256.ormlite.field.{DataType, DatabaseField}
import okhttp3.{ConnectionPool, OkHttpClient, Request}
import com.github.shadowsocks.R

import scala.util.Try

object SSRSub {

  // custom dns
  // https://github.com/square/okhttp#requirements
   def getSubscriptionResponse (url: String): Try[String] = Try{
    if (Build.VERSION.SDK_INT < 21) {
      return getSubscriptionResponse4(url)
    }
     val builder = new OkHttpClient.Builder()
       .connectTimeout(60, TimeUnit.SECONDS)
       .writeTimeout(60, TimeUnit.SECONDS)
       .readTimeout(60, TimeUnit.SECONDS)
       .connectionPool(new ConnectionPool(16, 3, TimeUnit.MINUTES))
     val client = builder.build()
    val request = new Request.Builder()
      .url(url)
      .build()
    val response = client.newCall(request).execute()
    val code = response.code()
    if (code == 200) {
      val result = getResponseString(response)
      response.body().close()
      result
    } else {
      response.body().close()
      throw new Exception(app.getString(R.string.ssrsub_error, code: Integer))
    }
  }

  def getSubscriptionResponse4(url: String): Try[String] = Try{
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(60 * 1000)
    conn.setReadTimeout(60 * 1000)
    conn.connect()
    val code = conn.getResponseCode
    if (code ==  200) {
      var subscribes = ""
      autoClose(conn.getInputStream())(in => {
        subscribes = scala.io.Source.fromInputStream(in).mkString
        subscribes = new String(Base64.decode(subscribes, Base64.URL_SAFE))
      })
      subscribes
    } else {
      conn.disconnect()
      throw new Exception(app.getString(R.string.ssrsub_error, code: Integer))
    }
  }

  def getResponseString (response: okhttp3.Response): String = {
    var subscribes = ""
    val contentType = response.header("content-type", null)
    if (contentType != null && contentType.contains("application/octet-stream")) {
      autoClose(response.body().byteStream())(in => {
        subscribes = scala.io.Source.fromInputStream(in).mkString
        subscribes = new String(Base64.decode(subscribes, Base64.URL_SAFE))
      })
    } else {
      val resp = response.body().string.replaceAll("=", "")
          .replaceAll("\\+", "-")
          .replaceAll("/", "_")
      subscribes = new String(Base64.decode(resp, Base64.URL_SAFE), "UTF-8")
    }
    subscribes
  }

  def createSSRSub(responseString: String, requestURL: String, groupName: String = ""): Option[SSRSub] = {
    val profiles_ssr = Parser.findAll_ssr(responseString).toList
    if(profiles_ssr.nonEmpty) {
      val ssrsub = new SSRSub {
        url = requestURL
        updated_at = Utils.today
        url_group = if (!TextUtils.isEmpty(groupName)) groupName
        else if (!TextUtils.isEmpty(profiles_ssr.head.url_group)) profiles_ssr.head.url_group
        else new URL(requestURL).getHost
      }
      return Some(ssrsub)
    } else {
      val profiles_vmess = Parser.findAllVmess(responseString).toList
      if (profiles_vmess.nonEmpty) {
        val ssrsub = new SSRSub {
          url = requestURL
          updated_at = Utils.today
          url_group = if (TextUtils.isEmpty(groupName)) new URL(requestURL).getHost else groupName
        }
        return Some(ssrsub)
      }
    }
    None
  }

  implicit class SSRSubFunctions(ssrsub: SSRSub) {

    def addProfiles(responseString: String, subUrl: String =""): Unit = {
      var currentProfile = app.currentProfile
      val delete_profiles = app.profileManager.getAllProfilesBySSRSub(ssrsub) match {
        case Some(subProfiles) =>
          subProfiles.filter(profile=> profile.ssrsub_id <= 0 || profile.ssrsub_id == ssrsub.id)
        case _ => null
      }
      var limit_num = -1
      var encounter_num = 0
      val findAllSSR = (responseString: String) => {
        if (responseString.indexOf("MAX=") == 0) {
          limit_num = responseString.split("\\n")(0).split("MAX=")(1).replaceAll("\\D+","").toInt
        }
        var profiles_ssr = Parser.findAll_ssr(responseString)
        if (responseString.indexOf("MAX=") == 0) {
          profiles_ssr = scala.util.Random.shuffle(profiles_ssr)
        }
        profiles_ssr
      }
      val profiles = subUrl match {
        case url if url.indexOf("sub=1") > 0 => findAllSSR(responseString)
        case url if url.indexOf("sub=3") > 0 => Parser.findAllVmess(responseString)
        case _ => findAllSSR(responseString) ++ Parser.findAllVmess(responseString)
      }
//      if (responseString.indexOf("MAX=") == 0) {
//        limit_num = responseString.split("\\n")(0).split("MAX=")(1).replaceAll("\\D+","").toInt
//      }
//      var profiles_ssr = Parser.findAll_ssr(responseString)
//      if (responseString.indexOf("MAX=") == 0) {
//        profiles_ssr = scala.util.Random.shuffle(profiles_ssr)
//      }
//      val profiles_vmess = Parser.findAllVmess(responseString)
//      val profiles = profiles_ssr ++ profiles_vmess
      var isProfileAdded = false
      profiles.foreach((profile: Profile) => {
        if (encounter_num < limit_num && limit_num != -1 || limit_num == -1) {
          profile.ssrsub_id = ssrsub.id
          profile.url_group = ssrsub.url_group
//          notifyGroupNameChange(Some(profile.url_group))
          app.profileManager.createProfile_sub(profile)
          isProfileAdded = true
          //        if (result != 0) {
          //          delete_profiles = delete_profiles.filter(_.id != result)
          //        }
        }
        encounter_num += 1
      })

      delete_profiles.foreach((profile: Profile) => {
        if (profile.id != app.profileId) {
          app.profileManager.delProfile(profile.id)
        }
        // keep current selected profile
        if (profile.id == app.profileId && isProfileAdded) {
          app.profileManager.delProfile(profile.id)
          currentProfile
            .flatMap(profile => Option(app.profileManager.checkLastExistProfile(profile)))
            .foreach(profile => {
              app.profileId(profile.id)
              currentProfile = app.currentProfile
            })
        }
      })
      // set current profile
      //    app.profileManager.getFirstProfileBySSRSub(ssrsub).foreach(p => app.profileId(p.id))
    }
  }

  }

// TODO: TIME
class SSRSub {
  @DatabaseField(generatedId = true)
  var id: Int = 0

  @DatabaseField
  var url: String = ""

  @DatabaseField
  var url_group: String = ""

  @DatabaseField
  var updated_at: String = ""
}
