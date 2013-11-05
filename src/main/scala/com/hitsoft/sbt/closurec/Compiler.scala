package com.hitsoft.sbt.closurec

import com.google.javascript.jscomp.{Compiler => ClosureCompiler, SourceFile, CompilerOptions, JSError}

import sbt._

import java.util

class Compiler(options: CompilerOptions) {

  def toJList[T](l: List[T]): util.List[T] = {
    val a = new util.ArrayList[T]
    l.map(a.add(_))
    a
  }

  def compile(sources: List[File], externs: List[File], target: File, log: Logger): Unit = {
    val compiler = new ClosureCompiler

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

      IO.createDirectory(file(target.getParent))
      IO.write(target, compiler.toSource)
    }
  }
}
