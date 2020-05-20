package com.github.shadowsocks.types

object Composed {
  implicit class ComposedList[A](fga: List[List[A]]) {
    def nestedMap[B](f: A => B): List[List[B]] = fga.map(ga => ga.map(f))
  }
}
