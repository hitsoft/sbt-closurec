package com.hitsoft.sbt.closurec

import java.nio.charset.Charset
import sbt._
import sbt.Keys._
import com.google.javascript.jscomp.{WarningLevel, CompilationLevel, VariableRenamingPolicy, CompilerOptions}
import java.util.regex.Pattern

object Plugin extends Plugin {

  import ClosureKeys._

  object ClosureKeys {
    lazy val closure = TaskKey[Seq[File]]("closure", "Compiles .jsm javascript manifest files")
    lazy val cleanJs = TaskKey[Unit]("cleanJs", "cleans js compiled dir after calcDeps")
    lazy val calcDeps = TaskKey[Seq[File]]("calcDeps", "calculates js files dependencies by goog.require, goog.provide")
    lazy val externsIncludeFilter = SettingKey[FileFilter]("externs-include-filter", "Filter for including externs sources and resources files from default directories.", closure)
    lazy val externsExcludeFilter = SettingKey[FileFilter]("externs-exclude-filter", "Filter for excluding externs sources and resources files from default directories.", closure)
    lazy val entryIncludeFilter = SettingKey[FileFilter]("entry-include-filter", "Filter for including entry sources and resources files from default directories.", closure)
    lazy val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8")
    lazy val closureOptions = SettingKey[CompilerOptions]("options", "Compiler options")
    lazy val suffix = SettingKey[String]("suffix", "String to append to output filename (before file extension)")
    lazy val variableRenamingPolicy = SettingKey[VariableRenamingPolicy]("js-variable-renaming-policy", "Javascript variable renaming policy (default local only)")
    lazy val prettyPrint = SettingKey[Boolean]("js-pretty-print", "Whether to pretty print Javascript (default false)")
    lazy val strictMode = SettingKey[Boolean]("js-strict-mode", "Whether to strict mode Javascript (default false)")
    lazy val compilationLevel = SettingKey[CompilationLevel]("js-compilation-level", "Closure Compiler compilation level")
    lazy val warningLevel = SettingKey[WarningLevel]("js-warning-level", "Closure Compiler warning level")
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
      sourceDirectory in closure <<= (sourceDirectory in conf) {
        _ / "javascript"
      },
      resourceManaged in closure <<= (resourceManaged in conf) {
        _ / "js"
      },
      sourceDirectory in calcDeps <<= (sourceDirectory in conf) {
        _ / "javascript"
      },
      resourceManaged in calcDeps <<= (resourceManaged in conf) {
        _ / "js"
      },
      cleanFiles in closure <<= (resourceManaged in closure)(_ :: Nil),
      cleanFiles in calcDeps <<= (resourceManaged in calcDeps)(_ :: Nil),
      watchSources <<= (unmanagedSources in closure),
      watchSources <<= (unmanagedSources in calcDeps)
    )) ++ Seq(
      cleanFiles <++= (cleanFiles in closure in conf),
      watchSources <++= (watchSources in closure in conf),
      cleanFiles <++= (cleanFiles in calcDeps in conf),
      watchSources <++= (watchSources in calcDeps in conf),
      resourceGenerators in conf <+= closure in conf,
      resourceGenerators in conf <+= calcDeps in conf,
      compile in conf <<= (compile in conf).dependsOn(calcDeps in conf),
      cleanJs in conf <<= (cleanJs in conf).dependsOn(compile in conf),
      closure in conf <<= (closure in conf).dependsOn(cleanJs in conf),
      packageBin in conf <<= (packageBin in conf).dependsOn(closure in conf)
    )

  object ExternsFileFilter extends FileFilter {
    val p = Pattern.compile("^.*/externs/[^/]*\\.js$", Pattern.CASE_INSENSITIVE)

    def accept(path: File): Boolean = {
      p.matcher(path.getCanonicalPath).matches()
    }
  }

  def closureSettings0: Seq[Setting[_]] = Seq(
    charset in closure := Charset.forName("utf-8"),
    prettyPrint := false,
    closureOptions <<= closureOptionsSetting,
    includeFilter in closure := "*.js",
    excludeFilter in closure := (".*" - ".") || HiddenFileFilter || ExternsFileFilter,
    externsIncludeFilter in closure := ExternsFileFilter,
    externsExcludeFilter in closure := (".*" - ".") || HiddenFileFilter,
    entryIncludeFilter in closure := "*.entry.js",
    includeFilter in calcDeps := "*.js",
    excludeFilter in calcDeps := (".*" - ".") || HiddenFileFilter || ExternsFileFilter,
    suffix in closure := "",
    unmanagedSources in closure <<= closureSourcesTask,
    unmanagedSources in calcDeps <<= calcDepsSourcesTask,
    clean in closure <<= closureCleanTask,
    clean in calcDeps <<= calcDepsCleanTask,
    cleanJs <<= calcDepsCleanTask,
    closure <<= closureCompilerTask,
    calcDeps <<= calcDepsTask,
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
    (streams,
      sourceDirectory in closure,
      resourceManaged in closure,
      includeFilter in closure,
      excludeFilter in closure,
      externsIncludeFilter in closure,
      externsExcludeFilter in closure,
      entryIncludeFilter in closure,
      charset in closure,
      closureOptions in closure,
      suffix in closure) map {
      (out,
       sources,
       target,
       include,
       exclude,
       externsInclude,
       externsExclude,
       entryInclude,
       charset,
       options,
       suffix) => {
        val src = sources.descendantsExcept(include, exclude).get.toList
        val externs = sources.descendantsExcept(externsInclude, externsExclude).get.toList
        // compile changed sources
        (for {
          entry <- sources.descendantsExcept(entryInclude, exclude).get
          outFile <- computeOutFile(sources, entry, target, suffix)
          if !sources.descendantsExcept(include, exclude).get.filter(_ newerThan outFile).isEmpty
        } yield {
          (entry, outFile)
        }) match {
          case Nil =>
            out.log.debug("No JavaScript entry files to compile")
          case xs =>
            out.log.info("Compiling %d entry files to %s" format(xs.size, target))
            xs map doCompile(out.log, options, src, externs)
            out.log.debug("Compiled %s entry files" format xs.size)
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
      includeFilter in calcDeps, excludeFilter in calcDeps) map {
      (out, sources, target, include, exclude) => {
        val outFile = target / "deps.js"
        val newSources = sources.descendantsExcept(include, exclude).get.filter(_ newerThan outFile)
        if (!newSources.isEmpty) {
          val src = sources.descendantsExcept(include, exclude).get
          src.foreach(src => IO.copyFile(src, targetFile(sources, src, target)))
          val tgt = target.descendantsExcept(include, exclude).get
          GoogDeps.apply(tgt).saveToFile(outFile)
        }
        compiled(target)
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

  private def doCompile(log: Logger, options: CompilerOptions, src: List[File], externs: List[File])(pair: (File, File)) = {
    val (entry, out) = pair
    log.debug("Compiling %s" format entry)
    val compiler = new Compiler(options)
    compiler.compile(GoogDeps.filterByDependencies(entry, src), externs, out, log)
  }

  private def compiled(under: File) = (under ** "*.js").get

  private def computeOutFile(sources: File, manifest: File, targetDir: File, suffix: String): Option[File] = {
    val outFile = IO.relativize(sources, manifest).get.replaceAll("\\.entry\\.js$", "").replaceAll("\\.js$", "") + {
      if (suffix.length > 0) "-%s.min.js".format(suffix)
      else ".min.js"
    }
    Some(new File(targetDir, outFile))
  }
}
