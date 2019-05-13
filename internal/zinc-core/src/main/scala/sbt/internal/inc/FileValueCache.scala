/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import xsbti.compile.analysis.{ Stamp => XStamp }

sealed trait FileValueCache[T] {
  def clear(): Unit
  def get: File => T
}

private[this] final class FileValueCache0[T](getStamp: File => XStamp, make: File => T)(
    implicit equiv: Equiv[XStamp]
) extends FileValueCache[T] {
  private[this] val backing = new ConcurrentHashMap[File, FileCache]

  def clear(): Unit = backing.clear()
  def get = file => {
    val ifAbsent = new FileCache(file)
    val cache = backing.putIfAbsent(file, ifAbsent)
    (if (cache eq null) ifAbsent else cache).get()
  }

  private[this] final class FileCache(file: File) {
    private[this] var stampedValue: Option[(XStamp, T)] = None
    def get(): T = synchronized {
      val latest = getStamp(file)
      stampedValue match {
        case Some((stamp, value)) if (equiv.equiv(latest, stamp)) => value
        case _                                                    => update(latest)
      }
    }

    private[this] def update(stamp: XStamp): T = {
      val value = make(file)
      stampedValue = Some((stamp, value))
      value
    }
  }
}
object FileValueCache {
  def apply[T](f: File => T): FileValueCache[T] = make(Stamper.forLastModified)(f)
  def make[T](stamp: File => XStamp)(f: File => T): FileValueCache[T] =
    new FileValueCache0[T](stamp, f)
}
