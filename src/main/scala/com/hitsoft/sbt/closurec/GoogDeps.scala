package com.hitsoft.sbt.closurec

import sbt._
import scala.collection.mutable
import scala.io.Source
import java.util.regex.Pattern
import java.io.PrintWriter

/**
 * User: smeagol
 * Date: 09.11.13
 * Time: 19:36
 */
class GoogDeps {

  import GoogDeps._

  private val deps = mutable.ListBuffer.empty[Dep]

  def collect(files: Seq[File]) = {
    for (file <- files) {
      deps.append(readDepFromFile(file))
    }
  }

  def forArray(list: Seq[String]): String = {
    val sb = new StringBuilder
    for (str <- list) {
      if (!sb.isEmpty)
        sb.append(", ")
      sb.append("'").append(str).append("'")
    }
    sb.toString()
  }

  def toString(base: File): String = {
    val sb = new StringBuilder
    sb.append("// This file was autogenerated\n// Please do not edit.")
    for (dep <- deps.toList) {
      sb.append("\ngoog.addDependency('%s', [%s], [%s]);".format(dep.file.relativeTo(base).get.getPath, forArray(dep.provide), forArray(dep.require)))
    }
    sb.toString()
  }

  def saveToFile(outFile: File) {
    val writer = new PrintWriter(outFile)
    try {
      writer.print(toString(outFile.getCanonicalFile.getParentFile))
    } finally {
      writer.close()
    }
  }
}

object GoogDeps {

  private case class Dep(file: File, provide: Seq[String], require: Seq[String])

  private val PATTERN_PROVIDE = Pattern.compile("^\\s*goog.provide\\s*\\(\\s*['\"](.*)['\"]\\s*\\)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE)
  private val PATTERN_REQUIRE = Pattern.compile("^\\s*goog.require\\s*\\(\\s*['\"](.*)['\"]\\s*\\)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE)

  private def readDepFromFile(file: sbt.File): GoogDeps.Dep = {
    val provide = mutable.ListBuffer.empty[String]
    val require = mutable.ListBuffer.empty[String]
    val src = Source.fromFile(file)
    for (line <- src.getLines()) {
      var m = PATTERN_PROVIDE.matcher(line)
      if (m.matches) {
        provide.append(m.group(1))
      } else {
        m = PATTERN_REQUIRE.matcher(line)
        if (m.matches()) {
          require.append(m.group(1))
        }
      }
    }
    new Dep(file, provide.sorted, require.sorted)
  }

  private def addRequirementWithDependencies(res: mutable.Set[sbt.File], deps: GoogDeps, require: String): Unit = {
    deps.deps.find(_.provide.contains(require)) match {
      case None =>
        throw new RuntimeException("Can not find JS providing required '%s'".format(require))
      case Some(dep) =>
        res.add(dep.file)
        dep.require.foreach(addRequirementWithDependencies(res, deps, _))
    }
  }

  def apply(files: Seq[File]): GoogDeps = {
    val res = new GoogDeps
    res.collect(files)
    res
  }

  def filterByDependencies(entry: File, files: Seq[File]): Seq[File] = {
    val dep = new GoogDeps
    dep.collect(files)
    val res = mutable.Set.empty[File]
    res.add(entry)
    for (namespace <- readDepFromFile(entry).require) {
      addRequirementWithDependencies(res, dep, namespace)
    }
    res.toList.sortBy(_.getCanonicalPath)
  }

}
