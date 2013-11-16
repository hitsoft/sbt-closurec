package com.hitsoft.sbt.closurec

import java.io.File
import sbt.IO
import sbt.Path._

class JsSourceMapping(val sourcesDir: File, val entryFile: File, val outDir: File, val jsSources: Seq[File], val suffix: String) {

  private def outPath = {
    IO.relativize(sourcesDir, entryFile).get.replaceFirst("\\.entry\\.js$", "").replaceFirst("\\.js$", "") + {
      if (suffix.isEmpty)
        ".min.js"
      else
        "-%s.min.js" format suffix
    }
  }

  val outFile = new File(outDir, outPath)

  def changed = (entryFile newerThan outFile) || (jsSources exists (_ newerThan outFile))

  override def toString = entryFile.toString
}
