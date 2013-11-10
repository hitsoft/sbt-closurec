seq(closureSettings:_*)

(ClosureKeys.compilationLevel in Compile) := CompilationLevel.ADVANCED_OPTIMIZATIONS

TaskKey[Unit]("check") <<= (baseDirectory, resourceManaged) map { (baseDirectory, resourceManaged) =>
  val fixture = sbt.IO.read(baseDirectory / "fixtures" / "app.min.js")
  val out = sbt.IO.read(resourceManaged / "main" / "js" / "app.min.js")
  if (out.trim != fixture.trim) error("unexpected output: \n\n" + out + "\n\n" + fixture)
  ()
}
