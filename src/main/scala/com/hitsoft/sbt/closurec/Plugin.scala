package com.hitsoft.sbt.closurec

import java.nio.charset.Charset
import sbt._
import sbt.Keys._
import com.google.javascript.jscomp.{WarningLevel, CompilationLevel, VariableRenamingPolicy, CompilerOptions}
import java.io.{Writer, InputStreamReader}
import scala.io.Source

object Plugin extends Plugin {
  import ClosureKeys._

  object ClosureKeys {
    lazy val closure = TaskKey[Seq[File]]("closure", "Compiles .jsm javascript manifest files")
    lazy val cleanJs = TaskKey[Unit]("cleanJs", "cleans js compiled dir after calcDeps")
    lazy val calcDeps = TaskKey[Seq[File]]("calcDeps", "calculates js files dependencies by goog.require, goog.provide")
    lazy val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8")
    lazy val downloadDirectory = SettingKey[File]("download-dir", "Directory to download ManifestUrls to")
    lazy val closureOptions = SettingKey[CompilerOptions]("options", "Compiler options")
    lazy val suffix = SettingKey[String]("suffix", "String to append to output filename (before file extension)")
    lazy val variableRenamingPolicy = SettingKey[VariableRenamingPolicy]("js-variable-renaming-policy", "Javascript variable renaming policy (default local only)")
    lazy val prettyPrint = SettingKey[Boolean]("js-pretty-print", "Whether to pretty print Javascript (default false)")
    lazy val strictMode = SettingKey[Boolean]("js-strict-mode", "Whether to strict mode Javascript (default false)")
    lazy val compilationLevel = SettingKey[CompilationLevel]("js-compilation-level", "Closure Compiler compilation level")
    lazy val warningLevel = SettingKey[WarningLevel]("js-warning-level", "Closure Compiler warning level")
    lazy val extractDepsWriter = TaskKey[Unit]("extractDepsWriter", "extracts depswriter.py from package")
  }

  /** Provide quick access to the enum values in com.google.javascript.jscomp.VariableRenamingPolicy */
  object VariableRenamingPolicy {
    val ALL = com.google.javascript.jscomp.VariableRenamingPolicy.ALL
    val LOCAL = com.google.javascript.jscomp.VariableRenamingPolicy.LOCAL
    val OFF = com.google.javascript.jscomp.VariableRenamingPolicy.OFF
    val UNSPECIFIED = com.google.javascript.jscomp.VariableRenamingPolicy.UNSPECIFIED
  }

  object WarningLevel {
    val QUIET = com.google.javascript.jscomp.WarningLevel.QUIET
    val DEFAULT = com.google.javascript.jscomp.WarningLevel.DEFAULT
    val VERBOSE = com.google.javascript.jscomp.WarningLevel.VERBOSE
  }

  object CompilationLevel {
    val WHITESPACE_ONLY = com.google.javascript.jscomp.CompilationLevel.WHITESPACE_ONLY
    val SIMPLE_OPTIMIZATIONS = com.google.javascript.jscomp.CompilationLevel.SIMPLE_OPTIMIZATIONS
    val ADVANCED_OPTIMIZATIONS = com.google.javascript.jscomp.CompilationLevel.ADVANCED_OPTIMIZATIONS
  }

  def closureOptionsSetting =
    (streams,
      variableRenamingPolicy in closure,
      prettyPrint in closure,
      strictMode in closure,
      compilationLevel in closure,
      warningLevel in closure) apply {
      (out, variableRenamingPolicy, prettyPrint, strictMode, compilationLevel, warningLevel) =>
        val options = new CompilerOptions

        options.variableRenaming = variableRenamingPolicy
        options.prettyPrint = prettyPrint

        compilationLevel.setOptionsForCompilationLevel(options)
        warningLevel.setOptionsForWarningLevel(options)

        if (strictMode) {
          options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT)
        } else {
          options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5)
        }

        options
    }

  def closureSettings: Seq[Setting[_]] =
    closureSettingsIn(Compile) ++ closureSettingsIn(Test)

  def closureSettingsIn(conf: Configuration): Seq[Setting[_]] =
    inConfig(conf)(closureSettings0 ++ Seq(
      sourceDirectory in closure <<= (sourceDirectory in conf) { _ / "javascript" },
      resourceManaged in closure <<= (resourceManaged in conf) { _ / "js" },
      sourceDirectory in calcDeps <<= (sourceDirectory in conf) { _ / "javascript" },
      resourceManaged in calcDeps <<= (resourceManaged in conf) { _ / "js" },
      downloadDirectory in closure <<= (target in conf) { _ / "closure-downloads" },
      target in extractDepsWriter <<= (target in extractDepsWriter) { _ / "closure-bin" },
      cleanFiles in closure <<= (resourceManaged in closure, downloadDirectory in closure)(_ :: _ :: Nil),
      cleanFiles in calcDeps <<= (resourceManaged in calcDeps)(_ :: Nil),
      watchSources <<= (unmanagedSources in closure),
      watchSources <<=(unmanagedSources in calcDeps)
    )) ++ Seq(
      cleanFiles <++= (cleanFiles in closure in conf),
      watchSources <++= (watchSources in closure in conf),
      cleanFiles <++= (cleanFiles in calcDeps in conf),
      watchSources <++= (watchSources in calcDeps in conf),
      resourceGenerators in conf <+= closure in conf,
      resourceGenerators in conf <+= calcDeps in conf,
      calcDeps in conf <<= (calcDeps in conf).dependsOn(extractDepsWriter in conf),
      compile in conf <<= (compile in conf).dependsOn(calcDeps in conf),
      cleanJs in conf <<= (cleanJs in conf).dependsOn(compile in conf),
      closure in conf <<= (closure in conf).dependsOn(cleanJs in conf),
      packageBin in conf <<= (packageBin in conf).dependsOn(closure in conf)
    )

  def closureSettings0: Seq[Setting[_]] = Seq(
    charset in closure := Charset.forName("utf-8"),
    prettyPrint := false,
    closureOptions <<= closureOptionsSetting,
    includeFilter in closure := "*.jsm",
    excludeFilter in closure := (".*" - ".") || HiddenFileFilter,
    includeFilter in calcDeps := "*.js",
    excludeFilter in calcDeps := (".*" - ".") || HiddenFileFilter,
    suffix in closure := "",
    unmanagedSources in closure <<= closureSourcesTask,
    unmanagedSources in calcDeps <<= calcDepsSourcesTask,
    clean in closure <<= closureCleanTask,
    clean in calcDeps <<= calcDepsCleanTask,
    cleanJs <<= calcDepsCleanTask,
    closure <<= closureCompilerTask,
    calcDeps <<= calcDepsTask,
    extractDepsWriter <<= extractDepsWriterTask,
    variableRenamingPolicy := VariableRenamingPolicy.LOCAL,
    prettyPrint := false,
    strictMode := false,
    compilationLevel := CompilationLevel.SIMPLE_OPTIMIZATIONS,
    warningLevel := WarningLevel.DEFAULT
  )

  private def closureCleanTask =
    (streams, resourceManaged in closure) map {
      (out, target) =>
        out.log.info("Cleaning generated by closure JavaScript under " + target)
        IO.delete(target)
    }

  private def calcDepsCleanTask =
    (streams, resourceManaged in calcDeps) map {
      (out, target) =>
        out.log.info("Cleaning generated by calcDeps JavaScript under " + target)
        IO.delete(target)
    }

  private def closureCompilerTask =
    (streams, sourceDirectory in closure, resourceManaged in closure,
     includeFilter in closure, excludeFilter in closure, charset in closure,
     downloadDirectory in closure, closureOptions in closure, suffix in closure) map {
      (out, sources, target, include, exclude, charset, downloadDir, options, suffix) => {
        // compile changed sources
        (for {
          manifest <- sources.descendantsExcept(include, exclude).get
          outFile <- computeOutFile(sources, manifest, target, suffix)
          if (manifest newerThan outFile)
        } yield { (manifest, outFile) }) match {
          case Nil =>
            out.log.debug("No JavaScript manifest files to compile")
          case xs =>
            out.log.info("Compiling %d jsm files to %s" format(xs.size, target))
            xs map doCompile(downloadDir, charset, out.log, options)
            out.log.debug("Compiled %s jsm files" format xs.size)
        }
        compiled(target)
      }
    }

  private def targetFile(sourceDir: File, sourceFile: File, targetDir: File) = {
    val targetName = IO.relativize(sourceDir, sourceFile).get
    new File(targetDir, targetName)
  }

  private def calcDepsTask =
    (streams, sourceDirectory in calcDeps, resourceManaged in calcDeps,
      includeFilter in calcDeps, excludeFilter in calcDeps, target in extractDepsWriter) map {
      (out, sources, target, include, exclude, bin) => {
        // compile changed sources
        val outFile = target / "deps.js"
        val newSources = sources.descendantsExcept(include, exclude).get.filter(_ newerThan outFile)
        if (!newSources.isEmpty) {
          sources.descendantsExcept(include, exclude).get.foreach(src => IO.copyFile(src, targetFile(sources, src, target)))
          Process(Seq("python", (bin / "depswriter.py").getCanonicalPath, "--root", target.getCanonicalPath, "--output_file", outFile.getCanonicalPath)).! match {
            case 0 => Some(outFile)
            case n => sys.error("Could not prepare deps file %s".format(outFile))
          }
        }
        compiled(target)
      }
    }

  private def extractDepsWriterTask =
    (streams, target in extractDepsWriter) map {
      (out, dir) =>
        if (!dir.exists) {
          IO.createDirectory(dir)
          IO.transfer(getClass.getResourceAsStream("/closure/depswriter.py"), dir / "depswriter.py")
          IO.transfer(getClass.getResourceAsStream("/closure/source.py"), dir / "source.py")
          IO.transfer(getClass.getResourceAsStream("/closure/treescan.py"), dir / "treescan.py")
        }
    }

  private def closureSourcesTask =
    (sourceDirectory in closure, includeFilter in closure, excludeFilter in closure) map {
      (sourceDir, incl, excl) =>
         sourceDir.descendantsExcept(incl, excl).get
    }

  private def calcDepsSourcesTask =
    (sourceDirectory in calcDeps, includeFilter in calcDeps, excludeFilter in calcDeps) map {
      (sourceDir, incl, excl) =>
        sourceDir.descendantsExcept(incl, excl).get
    }

  private def doCompile(downloadDir: File, charset: Charset, log: Logger, options: CompilerOptions)(pair: (File, File)) = {
    val (jsm, js) = pair
    log.debug("Compiling %s" format jsm)
    val srcFiles = Manifest.files(jsm, downloadDir, charset)
    val compiler = new Compiler(options)
    compiler.compile(srcFiles, Nil, js, log)
  }

  private def compiled(under: File) = (under ** "*.js").get

  private def computeOutFile(sources: File, manifest: File, targetDir: File, suffix: String): Option[File] = {
    val outFile = IO.relativize(sources, manifest).get.replaceAll("""[.]jsm(anifest)?$""", "") + {
      if (suffix.length > 0) "-%s.js".format(suffix)
      else ".js"
    }
    Some(new File(targetDir, outFile))
  }
}
