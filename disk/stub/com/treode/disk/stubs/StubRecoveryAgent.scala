package com.treode.disk.stubs

import java.nio.file.{Path, Paths}
import scala.util.Random

import com.treode.async.{Async, Callback, Scheduler}
import com.treode.async.io.File
import com.treode.async.io.stubs.StubFile
import com.treode.disk._

import Async.{guard, supply}
import Callback.ignore
import Disks.Launch
import StubDisks.StubRecovery

private class StubRecoveryAgent (implicit
    random: Random,
    scheduler: Scheduler,
    config: StubConfig
) extends StubRecovery {

  private val records = new RecordRegistry
  private var open = true

  def requireOpen(): Unit =
    require (open, "Recovery has already begun.")

  def replay [R] (desc: RecordDescriptor [R]) (f: R => Any): Unit =
    synchronized {
      requireOpen()
      records.replay (desc) (f)
    }

  def reattach (disk: StubDiskDrive): Async [Launch] =
    guard {
      synchronized {
        requireOpen()
        open = false
      }
      for {
        _ <- disk.replay (records)
      } yield {
        val releaser = new StubReleaser (disk)
        val disks = new StubDisks (releaser) (random, scheduler, disk, config)
        new StubLaunchAgent (releaser, disks) (random, scheduler, disk, config)
      }}

  def attach (disk: StubDiskDrive): Async [Launch] =
    supply {
      synchronized {
        requireOpen()
        open = false
      }
      val releaser = new StubReleaser (disk)
      val disks = new StubDisks (releaser) (random, scheduler, disk, config)
      new StubLaunchAgent (releaser, disks) (random, scheduler, disk, config)
    }

  def reattach (items: Path*): Async [Launch] =
    guard (throw new UnsupportedOperationException ("The StubDisks do not use files."))

  def attach (items: (Path, DiskGeometry)*): Async [Launch] =
    guard (throw new UnsupportedOperationException ("The StubDisks do not use files."))
}