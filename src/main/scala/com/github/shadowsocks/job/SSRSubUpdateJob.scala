package com.github.shadowsocks.job

import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

import com.evernote.android.job.Job.{Params, Result}
import com.evernote.android.job.{Job, JobRequest}
import com.github.shadowsocks.utils.IOUtils
import com.github.shadowsocks.ShadowsocksApplication.app
import okhttp3._
import java.util.concurrent.TimeUnit
import java.io.IOException

import com.github.shadowsocks.database._
import com.github.shadowsocks.utils.CloseUtils._
import com.github.shadowsocks.utils._
import android.util.{Base64, Log}
import android.widget.Toast
import android.content.Context
import android.os.Looper
import com.github.shadowsocks.R

import scala.util.{Failure, Success, Try}

/**
  * @author Mygod
  */
object SSRSubUpdateJob {
  final val TAG = "SSRSubUpdateJob"

  def schedule() = new JobRequest.Builder(SSRSubUpdateJob.TAG)
    .setPeriodic(TimeUnit.DAYS.toMillis(1))
    .setRequirementsEnforced(true)
    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
    .setRequiresCharging(false)
    .setUpdateCurrent(true)
    .build().schedule()
}

class SSRSubUpdateJob() extends Job {
  override def onRunJob(params: Params): Result = {
    Looper.prepare()
    if (app.settings.getInt(Key.ssrsub_autoupdate, 0) == 1) {
      app.ssrsubManager.getAllSSRSubs.flatMap(subs => {
        val autoSubs = subs.filter(_.enable_auto_update)
        if (autoSubs.isEmpty) None else Some(autoSubs)
      }) match {
        case Some(ssrsubs) =>
          val successCount = ssrsubs.map(ssrsub =>
            SSRSub.getSubscriptionResponse(ssrsub.url)
            .flatMap(response => Try {
              ssrsub.addProfiles(response, ssrsub.url)
              ssrsub.updated_at = Utils.today
              app.ssrsubManager.updateSSRSub(ssrsub)
              1
            }).recover {
            case e: Exception => 0
          }.getOrElse(0)).sum
          app.SSRSubUpdateJobFinished = true
          val result = if (successCount == ssrsubs.length) 1 else 0
          if (result == 1) {
            Log.i(SSRSubUpdateJob.TAG, "update subscriptions successfully!")
            Toast.makeText(app, app.resources.getString(R.string.ssrsub_toast_success), Toast.LENGTH_SHORT).show
            Looper.loop()
            Result.SUCCESS
          } else {
            Log.i(SSRSubUpdateJob.TAG, "update subscriptions failed!")
            Toast.makeText(app, app.resources.getString(R.string.ssrsub_toast_fail), Toast.LENGTH_SHORT).show
            Looper.loop()
            Result.RESCHEDULE
          }
        case _ => {
          Log.i(SSRSubUpdateJob.TAG, "no subscriptions found!")
          Toast.makeText(app, app.resources.getString(R.string.ssrsub_toast_fail), Toast.LENGTH_SHORT).show
          Looper.loop()
          Result.FAILURE
        }
      }
    } else {
      Result.RESCHEDULE
    }
  }
}
