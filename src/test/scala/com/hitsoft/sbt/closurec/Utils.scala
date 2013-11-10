package com.hitsoft.sbt.closurec

import java.io.File
import java.net.URISyntaxException

/**
 * User: smeagol
 * Date: 09.11.13
 * Time: 19:44
 */
object Utils {
  def resFile(name: String): File = {
    val url = getClass.getResource(name)
    try {
      new File(url.toURI)
    } catch {
      case e: URISyntaxException => new File(url.getPath)
    }
  }
}
