package com.hitsoft.sbt.closurec

import org.scalatest.FlatSpec
import sbt._
import scala.io.Source

/**
 * User: smeagol
 * Date: 09.11.13
 * Time: 19:36
 */
class GoogDepsSpec extends FlatSpec {

  "GoogDeps" should "properly collect deps in flat directory" in {
    val base = Utils.resFile("/js/flat/")
    val deps = GoogDeps.apply((base ** "*.js").get.sortBy(_.getCanonicalPath))
    val fixture = Source.fromFile(base / "deps.js.fixture").getLines().mkString("\n")
    val test = deps.toString(base)
    assert(fixture == test)
  }

  it should "properly collect deps in recursive directory" in {
    val base = Utils.resFile("/js/recursive/")
    val deps = GoogDeps.apply((base ** "*.js").get.sortBy(_.getCanonicalPath))
    val fixture = Source.fromFile(base / "deps.js.fixture").getLines().mkString("\n")
    val test = deps.toString(base)
    assert(fixture == test)
  }

  it should "properly filter sources by dependencies in flat directory" in {
    val base = Utils.resFile("/js/flat/")
    val test = GoogDeps.filterByDependencies(base / "app.entry.js", (base ** "*.js").get.sortBy(_.getCanonicalPath))
    assert(2 == test.size)
    assert(base / "app.entry.js" == test(0))
    assert(base / "utils.js" == test(1))
  }

  it should "properly filter sources by dependencies in recursive directory" in {
    val base = Utils.resFile("/js/recursive/")
    val test = GoogDeps.filterByDependencies(base / "app.entry.js", (base ** "*.js").get.sortBy(_.getCanonicalPath))
    assert(5 == test.size)
    assert(base / "app.entry.js" == test(0))
    assert(base / "subdir1" / "_namespace.js" == test(1))
    assert(base / "subdir1" / "utils.js" == test(2))
    assert(base / "subdir2" / "_namespace.js" == test(3))
    assert(base / "subdir2" / "utils.js" == test(4))
  }
}
