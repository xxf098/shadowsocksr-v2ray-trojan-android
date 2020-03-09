package com.github.shadowsocks.types

import com.github.shadowsocks.ShadowsocksApplication.app
import com.github.shadowsocks.R

object Result {
  def apply[A](x: A = null.asInstanceOf[A]): Result[A] = new Result[A] {
    val data: A = x
    val msg: String = ""
    override def isFailure: Boolean = true
  }
}

sealed abstract class Result[+A] {
  val data: A
  val msg: String

  def isFailure: Boolean
}

abstract class Success[A] extends Result[A] {
  override def isFailure: Boolean = false
}

abstract class Failure[A] extends Result[A] {
  override def isFailure: Boolean = true
}

case class SuccessConnect (d: Long) extends Success[Long] {
  val data: Long = d
  val msg: String = app.getString(R.string.connection_test_available, data: java.lang.Long)
}

case class FailureConnect(errMsg: String) extends Failure[Long] {
  val data: Long = 0L
  val msg: String = app.getString(R.string.connection_test_error, errMsg)
}
