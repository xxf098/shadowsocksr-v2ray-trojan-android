package com.github.shadowsocks.utils

import android.nfc.Tag
import android.util.Log
import com.github.shadowsocks.types.Result

import scala.util.Try

object Retryer {
  def exponentialBackoff[A](attempts: Int, delay: Int): Retryer[A] = {
    var delayTime = 0
    new Retryer[A] {
      override val totalAttempt: Int = attempts

      override def nextDelay(): Int = {
        delayTime = delayTime + delay
        delayTime
      }
    }
  }
}

trait Strategy[A] {
  def on(func: Int => A,
         onSuccess: A => Result[A],
         onError: Exception => Result[A]): Result[A]
}

trait Retryer[A] extends Strategy[A] {
  val totalAttempt: Int
  def nextDelay(): Int

  override def on(func: Int => A,
                  onSuccess: A => Result[A],
                  onError: Exception => Result[A]): Result[A] = {
    var result = Result[A]()
    for (i <- 0 to totalAttempt if result.isFailure) {
      result = Try(func(i))
        .map(onSuccess)
        .recover {
          case e: Exception => onError(e)
        }.get
      if (i < totalAttempt && result.isFailure) {
        Thread.sleep(nextDelay())
      }
    }
    result
  }
}
