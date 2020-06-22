package com.github.shadowsocks.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SubscriptionService extends Service {
  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    return Service.START_NOT_STICKY
  }
}
