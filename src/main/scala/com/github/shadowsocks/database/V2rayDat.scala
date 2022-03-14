package com.github.shadowsocks

import java.io.{File, FileOutputStream}
import java.util.concurrent.TimeUnit

import android.os.Build
import android.webkit.URLUtil
import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.database.SSRSub.{decodeBase64, getResponseString, getSubscriptionResponse, getSubscriptionResponse4}
import com.github.shadowsocks.utils.CloseUtils.autoClose
import com.github.shadowsocks.utils.IOUtils
import okhttp3.{ConnectionPool, OkHttpClient, Request}

import scala.util.Try

object V2rayDat {

  def getChecksumResponse(url: String): Try[String] = Try{
    if (Build.VERSION.SDK_INT < 21) {
      throw new Exception("not support")
    }
    val builder = new OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .connectionPool(new ConnectionPool(16, 3, TimeUnit.MINUTES))
    val client = builder.build()
    val request = new Request.Builder()
      .url(url)
      .build()
    val response = client.newCall(request).execute()
    val code = response.code()
    if (code == 200) {
      val result = getChecksum(response)
      response.body().close()
      result
    } else {
      response.body().close()
      throw new Exception(app.getString(R.string.ssrsub_error, code: Integer))
    }
  }

  def getChecksum(response: okhttp3.Response): String = {
    var subscribes = ""
    val contentType = response.header("content-type", null)
    if (contentType != null && contentType.contains("application/octet-stream")) {
      autoClose(response.body().byteStream())(in => {
        subscribes = scala.io.Source.fromInputStream(in).mkString
      })
    } else {
      subscribes = response.body().string
    }
    subscribes
  }

  def downloadDatFile(url: String, destPath: String): Try[String] = Try{
    if (Build.VERSION.SDK_INT < 21) {
      throw new Exception("not support")
    }
    // delete previous download file
    val destFile = new File(destPath)
    if (destFile.exists()) { destFile.delete() }
    // download new file
    val builder = new OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .connectionPool(new ConnectionPool(16, 3, TimeUnit.MINUTES))
    val client = builder.build()
    val request = new Request.Builder()
      .url(url)
      .build()
    val response = client.newCall(request).execute()
    val code = response.code()
    if (code == 200) {
      var checksum = ""
      autoClose(response.body().byteStream())(in => {
        autoClose(new FileOutputStream(destPath))(out =>
          IOUtils.copy(in, out))
      })
      response.body().close()
      checksum
    } else {
      response.body().close()
      throw new Exception(app.getString(R.string.ssrsub_error, code: Integer))
    }
  }

  def renameDatFile(checksumPath: String, originPath: String): Try[Unit] = Try {
    if (Build.VERSION.SDK_INT < 21) {
      throw new Exception("not support")
    }
    val originFile = new File(originPath)
    val deleteFile = new File(s"${originPath}.delete")
    if (originFile.exists()) {
      originFile.renameTo(deleteFile)
    }
    val checksumPathFile = new File(checksumPath)
    if (checksumPathFile.exists()) {
      checksumPathFile.renameTo(originFile)
    }
    deleteFile.delete()
  }

}
