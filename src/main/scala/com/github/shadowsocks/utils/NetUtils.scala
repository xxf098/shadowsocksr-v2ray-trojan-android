package com.github.shadowsocks.utils

import java.io.IOException
import java.lang.System.currentTimeMillis
import java.net.{HttpURLConnection, Inet4Address, InetAddress, Socket, URL}
import java.util
import java.util.concurrent.TimeUnit

import android.os.{Build, SystemClock}
import android.util.Base64
import com.github.shadowsocks.R
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.SSRAction.profile
import com.github.shadowsocks.database.SSRSub.getSubscriptionResponse4
import com.github.shadowsocks.utils.CloseUtils.autoClose
import okhttp3.{ConnectionPool, Dns, OkHttpClient, Request}

import scala.util.{Success, Try}

object NetUtils {

  private val TAG = "NetUtils"

  def isPortAvailable(port: Int): Boolean = {
    // Assume no connection is possible.
    var result = true
    try {
      new Socket("127.0.0.1", port).close()
      result = false;
    } catch {
      case e: Exception => Unit
    }
    result
  }

  def testConnection(url: String, timeout: Int = 2): Long = {
    var elapsed = 0L
    val dns = new Dns {
      override def lookup(s: String): util.List[InetAddress] = {
        val address = if (!Utils.isNumeric(s)) {
          Utils.resolve(s, enableIPv6 = false, hostname="1.1.1.1") match {
            case Some(addr) => InetAddress.getByName(addr)
            case None => throw new IOException(s"Name Not Resolved: $s")
          }
        } else {
          InetAddress.getByName(s)
        }
        util.Arrays.asList(address)
      }
    }
    val builder = new OkHttpClient.Builder()
      .connectTimeout(timeout, TimeUnit.SECONDS)
      .writeTimeout(timeout, TimeUnit.SECONDS)
      .readTimeout(timeout, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .dns(dns)
    val client = builder.build()
    val request = new Request.Builder()
      .url(url)
      .removeHeader("Host").addHeader("Host", "www.google.com")
      .build()
    val response = client.newCall(request).execute()
    val code = response.code()
    if (code == 204 || code == 200 && response.body().contentLength == 0) {
      val start = currentTimeMillis
      val response = client.newCall(request).execute()
      elapsed = currentTimeMillis - start
      val code = response.code()
      if (code == 204 || code == 200 && response.body().contentLength == 0) {
        response.body().close()
      }
      else throw new Exception(app.getString(R.string.connection_test_error_status_code, code: Integer))
    } else throw new Exception(app.getString(R.string.connection_test_error_status_code, code: Integer))
    response.body().close()
    elapsed
  }

  def testConnectionStartup (url: String, timeout: Int = 2): Long = {
    if (Build.VERSION.SDK_INT < 21) {
      return testConnectionStartup4(url, timeout)
    }
    val dns = new Dns {
      override def lookup(s: String): util.List[InetAddress] = {
        val address = if (!Utils.isNumeric(s)) {
          Utils.resolve(s, enableIPv6 = false, hostname="1.1.1.1") match {
            case Some(addr) => InetAddress.getByName(addr)
            case None => throw new IOException(s"Name Not Resolved: $s")
          }
        } else {
          InetAddress.getByName(s)
        }
        util.Arrays.asList(address)
      }
    }
    // single client
    val client = new OkHttpClient.Builder()
      .connectTimeout(timeout, TimeUnit.SECONDS)
      .writeTimeout(timeout, TimeUnit.SECONDS)
      .readTimeout(timeout, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .connectionPool(new ConnectionPool(16, 3, TimeUnit.MINUTES))
      .dns(dns)
      .build()
    val request = new Request.Builder().url(url).build()
    client.newCall(request).execute().body().close()
    val start = SystemClock.elapsedRealtime()
    val response = client.newCall(request).execute()
    val elapsed = SystemClock.elapsedRealtime() - start
    if (response.code >= 300) {
      throw new Exception(app.getString(R.string.connection_test_error_status_code, response.code: Integer))
    }
    response.body().close()
    elapsed
  }

  // android 4.x
  def testConnectionStartup4 (url: String, timeout: Int = 2): Long = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(timeout * 1000)
    conn.setReadTimeout(timeout * 1000)
    val start = SystemClock.elapsedRealtime()
    conn.connect()
    val elapsed = SystemClock.elapsedRealtime() - start
    val code = conn.getResponseCode
    if (code >= 300) {
      conn.disconnect()
      throw new Exception(app.getString(R.string.connection_test_error_status_code, code: Integer))
    }
    elapsed
  }

}
