package example1

import java.net.{InetAddress, InetSocketAddress}
import java.nio.file.Paths

import com.treode.cluster.{CellId, ClusterConfig, HostId}
import com.treode.disk.{DiskConfig, DriveGeometry}
import com.treode.store.{Cohort, Store, StoreConfig}
import com.twitter.conversions.storage._
import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}
import com.twitter.util.StorageUnit

object Main extends AsyncFinatraServer {

  val init = flag [Boolean] ("init", false, "Initialize the database")

  val serve = flag [Boolean] ("serve", "Start the server (default !init)")

  val solo = flag [Boolean] ("solo", false, "Run the server solo")

  val cell = flag [CellId] ("cell", "Cell ID")

  val host = flag [HostId] ("host", "Host ID")

  val superBlockBits =
      flag [Int] ("superBlockBits",  14, "Size of the super block (log base 2)")

  val segmentBits =
      flag [Int] ("segmentBits", 30, "Size of a disk segment (log base 2)")

  val blockBits =
      flag [Int] ("blockBits", 13, "Size of a disk block (log base 2)")

  val diskBytes =
      flag [StorageUnit] ("diskBytes", 1.terabyte, "Maximum size of disk (bytes)")

  val port = flag [Int] ("port", 6278, "Address on which peers should connect")

  premain {
    LoggerFactory (
        node = "com.treode",
        level = Some (Level.INFO),
        handlers = ConsoleHandler() :: Nil
    ) .apply()
  }

  def _init() {

    if (!cell.isDefined || !host.isDefined) {
      println ("-cell and -host are required.")
      return
    }

    if (args.length == 0) {
      println ("At least one path is required.")
      return
    }

    val paths = args map (Paths.get (_))

    Store.init (
        host(),
        cell(),
        superBlockBits(),
        segmentBits(),
        blockBits(),
        diskBytes().inBytes,
        paths: _*)
  }

  def _serve() {

    if (!init() && (cell.isDefined || host.isDefined)) {
      println ("-cell and -host are ignored.")
      return
    }

    if (args.length == 0) {
      println ("At least one path is required.")
      return
    }

    implicit val diskConfig = DiskConfig.suggested.copy (superBlockBits = superBlockBits())
    implicit val clusterConfig = ClusterConfig.suggested
    implicit val storeConfig = StoreConfig.suggested

    val controller = {
      val c = Store.recover (
          bindAddr = new InetSocketAddress (port()),
          shareAddr = new InetSocketAddress (InetAddress.getLocalHost, port()),
          paths = args map (Paths.get (_)): _*)
      c.await()
    }

    register (new Resource (controller.hostId, controller.store))
    register (new Peers (controller))
    register (new Admin (controller))

    if (solo())
      controller.cohorts = Array (Cohort.settled (controller.hostId))

    onExit (controller.shutdown().await())

    super.main()
  }

  override def main() {
    if (init())
      _init()
    if (serve.isDefined && serve() || !serve.isDefined && !init())
      _serve()
    if (serve.isDefined && !serve() && !init())
      println ("Nothing to do.")
  }}