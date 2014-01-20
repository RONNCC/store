package com.treode.async

import java.util.ArrayList
import scala.collection.JavaConversions._

class Future [A] (scheduler: Scheduler) extends Callback [A] {

  private var callbacks = new ArrayList [Callback [A]]
  private var value = null.asInstanceOf [A]
  private var thrown = null.asInstanceOf [Throwable]

  def pass (v: A): Unit = synchronized {
    require (value == null && thrown == null, "Future was already set.")
    value = v
    val callbacks = this.callbacks
    this.callbacks = null
    callbacks foreach (scheduler.execute (_, v))
  }

  def fail (t: Throwable): Unit = synchronized {
    require (value == null && thrown == null, "Future was already set.")
    thrown = t
    val callbacks = this.callbacks
    this.callbacks = null
    callbacks foreach (_.fail (t))
  }

  def get (cb: Callback [A]): Unit = synchronized {
    if (value != null)
      cb (value)
    else if (thrown != null)
      cb.fail (thrown)
    else
      callbacks.add (cb)
  }}