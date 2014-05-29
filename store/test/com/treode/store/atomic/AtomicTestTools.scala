package com.treode.store.atomic

import scala.util.Random

import com.treode.async.Async
import com.treode.async.stubs.StubScheduler
import com.treode.async.stubs.implicits._
import com.treode.store._
import com.treode.store.locks.LockSet
import org.scalatest.Assertions

import Assertions.fail

private trait AtomicTestTools extends StoreTestTools {

  implicit class RichPrepareResult (actual: Async [PrepareResult]) {
    import PrepareResult._

    def expectPrepared (implicit s: StubScheduler): (TxClock, LockSet) =
      actual.pass match {
        case Prepared (vt, locks) =>
          (vt, locks)
        case _ =>
          fail (s"Expected Written, found ${actual}")
          throw new Exception
      }

    def expectCollided (ks: Int*) (implicit s: StubScheduler): Unit =
      actual.expect (Collided (ks))

    def expectStale (implicit s: StubScheduler): Unit =
      actual.expect (Stale)

    def abort() (implicit s: StubScheduler) {
      val (vt, locks) = expectPrepared
      locks.release()
    }}

  implicit class AtomicRichRandom (random: Random) {

    def nextKeys (ntables: Int, nkeys: Int, nops: Int): Set [(Long, Long)] = {
      var items = Set.empty [(Long, Long)]
      while (items.size < nops)
        items += ((math.abs (random.nextLong % ntables), math.abs (random.nextLong % nkeys)))
      items
    }

    def nextKeys (ntables: Int, nkeys: Int, nwrites: Int, nops: Int): Seq [Set [(Long, Long)]] =
      Seq.fill (nwrites) (nextKeys (ntables, nkeys, nops))

    def nextUpdate (key: (Long, Long)): WriteOp.Update =
      WriteOp.Update (TableId (key._1), Bytes (key._2), Bytes (random.nextInt (1<<20)))

    def nextUpdates (keys: Set [(Long, Long)]): Seq [WriteOp.Update] =
      keys.toSeq.map (nextUpdate _)
  }

  def expectAtlas (version: Int, cohorts: Cohort*) (hosts: Seq [StubAtomicHost]) {
    val atlas = Atlas (cohorts.toArray, version)
    for (host <- hosts)
      host.expectAtlas (atlas)
  }}

private object AtomicTestTools extends AtomicTestTools
