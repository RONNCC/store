package com.treode.store.atomic

import scala.collection.SortedMap
import scala.util.Random

import com.treode.async.{Async, Scheduler}
import com.treode.async.implicits._
import com.treode.store._

import Async.guard
import AtomicTestTools._

class AtomicTracker {

  private var attempted =
    Map .empty [(Long, Long), Set [Int]] .withDefaultValue (Set.empty)

  private var accepted =
    Map.empty [(Long, Long), SortedMap [TxClock, Int]] .withDefaultValue (SortedMap.empty (TxClock))

  private def writting (table: Long, key: Long, value: Int): Unit =
    synchronized {
      val tk = (table, key)
      attempted += tk -> (attempted (tk) + value)
    }

  private def writting (op: WriteOp.Update): Unit =
    writting (op.table.id, op.key.long, op.value.int)

  private def writting (ops: Seq [WriteOp.Update]): Unit =
    ops foreach (writting (_))

  private def wrote (table: Long, key: Long, value: Int, time: TxClock): Unit =
    synchronized {
      val tk = (table, key)
      attempted += tk -> (attempted (tk) - value)
      accepted += tk -> (accepted (tk) + ((time, value)))
    }

  private def wrote (op: WriteOp.Update, wt: TxClock): Unit =
    wrote (op.table.id, op.key.long, op.value.int, wt)

  private def wrote (ops: Seq [WriteOp.Update], wt: TxClock): Unit =
    ops foreach (wrote (_, wt))

  private def condition (table: Long, key: Long): TxClock =
    synchronized {
      val vs = accepted ((table, key))
      if (vs.isEmpty)
        TxClock.zero
      else
        vs.keySet.max
    }

  private def condition (op: WriteOp.Update): TxClock =
    condition (op.table.id, op.key.long)

  private def condition (ops: Seq [WriteOp.Update]): TxClock =
    ops .map (condition (_)) .max

  def write (host: StubAtomicHost, ops: Seq [WriteOp.Update]) (implicit random: Random): Async [Unit] =
    guard {
      writting (ops)
      val ct = condition (ops)
      for {
        wt <- host.write (random.nextTxId, ct, ops: _*)
      } yield {
        wrote (ops, wt)
      }
    } .recover {
      case _: StaleException => ()
      case _: TimeoutException => ()
    }

  def batch (
      ntables: Int,
      nkeys: Int,
      nwrites: Int,
      nops: Int,
      hs: StubAtomicHost*
  ) (implicit
      random: Random
  ): Async [Unit] = {
    val khs = for (ks <- random.nextKeys (ntables, nkeys, nwrites, nops); h <- hs) yield (ks, h)
    for ((ks, h) <- khs.latch.unit)
      write (h, random.nextUpdates (ks))
  }

  def batches (
      nbatches: Int,
      ntables: Int,
      nkeys: Int,
      nwrites: Int,
      nops: Int,
      hs: StubAtomicHost*
  ) (implicit
      random: Random,
      scheduler: Scheduler
  ): Async [Unit] =
    for (n <- (0 until nbatches) .async)
      batch (ntables, nkeys, nwrites, nops, hs:_*)

  def read (host: StubAtomicHost, table: Long, key: Long): Async [Int] =
    for {
      found <- host.read (TxClock.max, ReadOp (TableId (table), Bytes (key)))
    } yield {
      found.head.value.map (_.int) .getOrElse (-1)
    }

  def check (host: StubAtomicHost) (implicit scheduler: Scheduler): Async [Unit] =
    for {
      _ <- for ((tk, vs) <- accepted.async) {
            val expected = attempted (tk) + vs.maxBy (_._1) ._2
            for {
              found <- read (host, tk._1, tk._2)
            } yield {
              assert (
                  expected contains found,
                  s"Expected $tk to be one of $expected, found $found")
            }}
      _ <- for ((tk, vs) <- attempted.async; if !(accepted contains tk)) {
            val expected = vs + -1
            for {
              found <- read (host, tk._1, tk._2)
            } yield {
              assert (
                  expected contains found,
                  s"Expected $tk to be one of $expected, found $found")
            }}
    } yield ()
}
