package com.github.shadowsocks.services

import android.os.{Bundle, Handler, ResultReceiver}

trait GetResultCallBack {
  def getResult (code: Int, bundle: Bundle)
}

class BgResultReceiver(handler: Handler) extends ResultReceiver(handler){

  private var callback: GetResultCallBack = _

  def this(handler: Handler, cb: GetResultCallBack) = {
    this(handler)
    this.callback = cb
  }

  override def onReceiveResult(resultCode: Int, resultData: Bundle): Unit = {
    if (callback != null) {
      callback.getResult(resultCode, resultData)
    }
  }



}
