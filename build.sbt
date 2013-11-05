sbtPlugin := true

organization := "com.hitsoft"

name := "closurec-sbt"

version <<= sbtVersion(v =>
  if (v.startsWith("0.13")) "0.1.0"
  else error("unsupported sbt version %s" format v)
)

libraryDependencies += "com.google.javascript" % "closure-compiler"   % "v20131014"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

seq(scriptedSettings:_*)

scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8")
