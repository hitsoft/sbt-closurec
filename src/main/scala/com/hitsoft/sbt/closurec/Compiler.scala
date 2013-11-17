package com.hitsoft.sbt.closurec

import com.google.javascript.jscomp.{Compiler => ClosureCompiler, SourceMap, SourceFile, CompilerOptions, JSError}

import sbt._

import java.util

class Compiler(options: CompilerOptions) {

  def toJList[T](l: Seq[T]): util.List[T] = {
    val a = new util.ArrayList[T]
    l.map(a.add)
    a
  }

  def compile(sources: Seq[File], externs: Seq[File], target: File, log: Logger, generateMapFile: Boolean): Unit = {
    val compiler = new ClosureCompiler

    val mapPath = target.getCanonicalPath + ".map"

    if (generateMapFile) {
      log.debug("Compiler -> generate source map to '%s'" format mapPath)
      options.setSourceMapOutputPath(mapPath)
      options.setSourceMapFormat(SourceMap.Format.V3)
      options.setSourceMapDetailLevel(SourceMap.DetailLevel.ALL)
    }

    val result = compiler.compile(
      toJList(externs.map(SourceFile.fromFile)),
      toJList(sources.map(SourceFile.fromFile)),
      options
    )

    val errors = result.errors.toList
    val warnings = result.warnings.toList

    if (!errors.isEmpty) {
      errors.foreach { (err: JSError) => log.error(err.toString) }
    }
    else {
      if (!warnings.isEmpty) {
        warnings.foreach { (err: JSError) => log.warn(err.toString) }
      }

      IO.createDirectory(target.getParentFile)
      IO.write(target, compiler.toSource)
      if (generateMapFile) {
        val sb = new java.lang.StringBuilder()
        result.sourceMap.appendTo(sb, target.getName)
        IO.write(file(mapPath), sb.toString)
      }
    }
  }
}
