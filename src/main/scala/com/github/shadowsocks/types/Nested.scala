package com.github.shadowsocks.types

import scala.language.higherKinds

trait Nested[F[_], G[_], A] {
  def nestedMap[B](f: A=> B): F[G[B]]
  def nestedIndexMap[B](f: (A, Int, Int) => B): F[G[B]]
}

object Nested {
  implicit class NestedList[A](fga: List[List[A]]) extends Nested[List, List, A] {
    override def nestedMap[B](f: A => B): List[List[B]] = fga.map(ga => ga.map(f))

    def nestedIndexMap[B](f: (A, Int, Int) => B): List[List[B]] = {
      fga.indices.toList.map(index => fga(index).indices.toList.map(i => f(fga(index)(i), index, i)))
    }
  }
}
