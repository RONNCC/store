package com.treode.store.cluster.atomic

import com.treode.cluster.StubCluster
import com.treode.store._

private class TestableCluster (hosts: Seq [StubHost], cluster: StubCluster) extends TestableStore {

  private def random = cluster.random

  private def randomHost: StubHost =
    hosts (random.nextInt (hosts.size))

  def read (rt: TxClock, ops: Seq [ReadOp], cb: ReadCallback): Unit =
    randomHost.read (rt, ops, cb)

  def write (ct: TxClock, ops: Seq [WriteOp], cb: WriteCallback): Unit =
    randomHost.write (TxId (random.nextLong), ct, ops, cb)

  def expectCells (t: TableId) (expected: TimedCell*): Unit =
    hosts foreach (_.expectCells (t) (expected: _*))

  def runTasks() = cluster.runTasks()
}