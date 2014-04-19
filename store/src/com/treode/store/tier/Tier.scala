package com.treode.store.tier

import scala.util.{Failure, Success}

import com.treode.async.{Async, AsyncImplicits, Callback}
import com.treode.disk.{Disks, Position}
import com.treode.store.{Bytes, Cell, StorePicklers, TxClock}

import Async.{async, supply}
import AsyncImplicits._

private case class Tier (
    gen: Long,
    root: Position,
    bloom: Position,
    keys: Long,
    entries: Long,
    earliest: TxClock,
    latest: TxClock,
    diskBytes: Long
) {

  def ceiling (desc: TierDescriptor, key: Bytes, time: TxClock) (implicit disks: Disks): Async [Option [Cell]] =
    async { cb =>

      import desc.pager

      val loop = Callback.fix [TierPage] { loop => {

        case Success (p: IndexPage) =>
          val i = p.ceiling (key, time)
          if (i == p.size) {
            cb.pass (None)
          } else {
            val e = p.get (i)
            pager.read (e.pos) .run (loop)
          }

        case Success (p: CellPage) =>
          val i = p.ceiling (key, time)
          if (i == p.size)
            cb.pass (None)
          else
            cb.pass (Some (p.get (i)))

        case Success (p @ _) =>
          cb.fail (new MatchError (p))

        case Failure (t) =>
          cb.fail (t)
      }}

      pager.read (bloom) .run {

        case Success (bloom: BloomFilter) if bloom.contains (Bytes.pickler, key) =>
          pager.read (root) .run (loop)

        case Success (_: BloomFilter) =>
          cb.pass (None)

        case Success (p @ _) =>
          cb.fail (new MatchError (p))

        case Failure (t) =>
          cb.fail (t)
      }}

  override def toString: String =
    s"Tier($gen,$root,$bloom)"
}

private object Tier {

  val pickler = {
    import StorePicklers._
    wrap (ulong, pos, pos, ulong, ulong, txClock, txClock, ulong)
    .build ((Tier.apply _).tupled)
    .inspect (v => (v.gen, v.root, v.bloom, v.keys, v.entries, v.earliest, v.latest, v.diskBytes))
  }}
