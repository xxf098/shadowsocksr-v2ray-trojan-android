package com.github.shadowsocks.job

import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

import com.evernote.android.job.Job.{Params, Result}
import com.evernote.android.job.{Job, JobRequest}
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.utils.CloseUtils._
import com.github.shadowsocks.utils.{IOUtils, Route}

/**
  * @author Mygod
  */
object AclSyncJob {
  final val TAG = "AclSyncJob"

  def schedule(route: String) = new JobRequest.Builder(AclSyncJob.TAG + ':' + route)
    .setExecutionWindow(1, TimeUnit.DAYS.toMillis(5))
    .setRequirementsEnforced(true)
    .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
    .setRequiresCharging(true)
    .setUpdateCurrent(true)
    .build().schedule()
}

class AclSyncJob(route: String) extends Job {
  override def onRunJob(params: Params): Result = {
    val filename = route + ".acl"
    try {
      val aclUrl = route match {
        case x if Route.DEFAULT_ROUTES.contains(x) => Some(s"https://raw.githubusercontent.com/xxf098/shadowsocksr-v2ray-android/xxf098/master/src/main/assets/acl/$filename")
        case x if Route.ACL4SSR_ROUTES.contains(x) => Some(s"https://cdn.jsdelivr.net/gh/ACL4SSR/ACL4SSR@master/$filename")
        case _ => None
      }
      aclUrl.foreach(url => {
        IOUtils.writeString(app.getApplicationInfo.dataDir + '/' + filename, autoClose(
          new URL(url).openConnection().getInputStream())(IOUtils.readString))
      })
//      if(Route.DEFAULT_ROUTES.contains(route))
//      {
//        //noinspection JavaAccessorMethodCalledAsEmptyParen
//        IOUtils.writeString(app.getApplicationInfo.dataDir + '/' + filename, autoClose(
//          new URL("https://raw.githubusercontent.com/shadowsocks/shadowsocks-android/master/core/src/main/assets/acl/" +
//            filename).openConnection().getInputStream())(IOUtils.readString))
//      }
      Result.SUCCESS
    } catch {
      case e: IOException =>
        e.printStackTrace()
        Result.RESCHEDULE
      case e: Exception =>  // unknown failures, probably shouldn't retry
        e.printStackTrace()
        Result.FAILURE
    }
  }
}
