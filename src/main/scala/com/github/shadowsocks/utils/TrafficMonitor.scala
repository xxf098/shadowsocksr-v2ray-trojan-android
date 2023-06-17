package com.github.shadowsocks.utils

import java.text.DecimalFormat
import java.util.Locale

import android.content.res.Configuration
import android.os.Build
import com.github.shadowsocks.R
import com.github.shadowsocks.ShadowsocksApplication.app
import android.text.format.Formatter

object TrafficMonitor {
  // Bytes per second
  var txRate: Long = _
  var rxRate: Long = _

  // Bytes for the current session
  var txTotal: Long = _
  var rxTotal: Long = _
  // traffic not saved to db
  var txSavedTotal: Long = _
  var rxSavedTotal: Long = _

  // Bytes for the last query
  var txLast: Long = _
  var rxLast: Long = _
  var timestampLast: Long = _
  @volatile var dirty = true

  private val units = Array("KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB", "DB", "CB")
  private val numberFormat = new DecimalFormat("@@@")
  private val numberFormatSimple = new DecimalFormat("@@")
  def formatTraffic(size: Long): String = formatTrafficInternal(size)

  def formatTrafficInternal(size: Long, simple: Boolean = false): String = {
    var n: Double = size
    var i = -1
    while (n >= 1000) {
      n /= 1024
      i = i + 1
    }
    if (simple) {
      if (i< 0) " " + size + app.getResources.getQuantityString(R.plurals.bytes, size.toInt)
      else numberFormatSimple.format(n) + units(i).substring(0, 1)
    } else {
      if (i< 0) size + " " + app.getResources.getQuantityString(R.plurals.bytes, size.toInt)
      else numberFormat.format(n) + ' ' + units(i)
    }
  }

  def updateRate() = {
    val now = System.currentTimeMillis()
    val delta = now - timestampLast
    var updated = false
    if (delta != 0) {
      if (dirty) {
        txRate = (txTotal - txLast) * 1000 / delta
        rxRate = (rxTotal - rxLast) * 1000 / delta
        txLast = txTotal
        rxLast = rxTotal
        dirty = false
        updated = true
      } else {
        if (txRate != 0) {
          txRate = 0
          updated = true
        }
        if (rxRate != 0) {
          rxRate = 0
          updated = true
        }
      }
      timestampLast = now
    }
    updated
  }

  def update(tx: Long, rx: Long) {
    if (txTotal < tx) {
      txSavedTotal = tx - txTotal + txSavedTotal
      txTotal = tx
      dirty = true
    }
    if (rxTotal < rx) {
      rxSavedTotal = rx - rxTotal + rxSavedTotal
      rxTotal = rx
      dirty = true
    }
  }

  def reset() {
    txRate = 0
    rxRate = 0
    txTotal = 0
    rxTotal = 0
    txSavedTotal = 0
    rxSavedTotal = 0
    txLast = 0
    rxLast = 0
    dirty = true
  }

  def checkNeedPersist(threshold: Long): Boolean = {
    txSavedTotal >= threshold || rxSavedTotal >= threshold
  }

  def persistStats(id: Int): Unit = {
    app.profileManager.getProfile(id) match {
      case Some(p) => {
        p.tx += txSavedTotal
        p.rx += rxSavedTotal
        txSavedTotal = 0
        rxSavedTotal = 0
        app.profileManager.updateProfileTraffic(p.id, p.tx, p.rx)
      }
      case None =>
    }
  }

  // increase save
  def increaseTraffic(id: Int): Unit = {
    val result = app.profileManager.increaseProfileTraffic(id, txSavedTotal, rxSavedTotal)
    if (result) {
      txSavedTotal = 0
      rxSavedTotal = 0
    }
  }
}
