package com.hitsoft.sbt.closurec

import java.nio.charset.Charset
import sbt._
import sbt.Keys._
import com.google.javascript.jscomp._
import java.util.regex.Pattern
import sbt.Configuration

object Plugin extends Plugin {

  import ClosureKeys._

  object ClosureKeys {
    lazy val closure = TaskKey[Seq[File]](
      "closure", "Compiles entry javascript files with Closure Compiler")
    lazy val cleanJs = TaskKey[Unit](
      "clean-js", "Cleans js output dir after calcDeps", closure)
    lazy val calcDeps = TaskKey[Seq[File]](
      "calc-deps", "Calculates js files dependencies by goog.require, goog.provide", closure)
    lazy val externsIncludeFilter = SettingKey[FileFilter](
      "externs-include-filter", "Filter for including externs sources and resources files from default directories.", closure)
    lazy val externsExcludeFilter = SettingKey[FileFilter](
      "externs-exclude-filter", "Filter for excluding externs sources and resources files from default directories.", closure)
    lazy val externsFiles = TaskKey[Seq[File]](
      "externs-files", "Externs files list", closure)
    lazy val entryFilter = SettingKey[FileFilter](
      "entry-include-filter", "Filter for specifying entry files from default directories.", closure)
    lazy val entryFiles = TaskKey[Seq[File]](
      "entry-files", "Entry files list", closure)
    lazy val charset = SettingKey[Charset](
      "charset", "Sets the character encoding used in file IO. Defaults to utf-8")
    lazy val closureOptions = SettingKey[CompilerOptions](
      "options", "Compiler options", closure)
    lazy val suffix = TaskKey[String](
      "suffix", "String to append to output filename (before file extension)", closure)
    lazy val jsVariableRenamingPolicy = SettingKey[VariableRenamingPolicy](
      "js-variable-renaming-policy", "Javascript variable renaming policy (default local only)", closure)
    lazy val jsPrettyPrint = SettingKey[Boolean](
      "js-pretty-print", "Whether to pretty print Javascript (default false)", closure)
    lazy val jsStrictMode = SettingKey[Boolean](
      "js-strict-mode", "Whether to strict mode Javascript (default false)", closure)
    lazy val jsCompilationLevel = SettingKey[CompilationLevel](
      "js-compilation-level", "Closure Compiler compilation level", closure)
    lazy val jsWarningLevel = SettingKey[WarningLevel](
      "js-warning-level", "Closure Compiler warning level", closure)
    lazy val jsGenerateMapFile = SettingKey[Boolean](
      "js-generate-map-file", "If true, Closure Compiler will generate map file (default false)", closure)
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
      jsVariableRenamingPolicy in closure,
      jsPrettyPrint in closure,
      jsStrictMode in closure,
      jsCompilationLevel in closure,
      jsWarningLevel in closure) apply {
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

  def closureSettingsManualCompileIn(conf: Configuration): Seq[Setting[_]] =
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
      watchSources <++= (watchSources in calcDeps in conf)
    )

  def closureSettingsManualCompile: Seq[Setting[_]] =
    closureSettingsManualCompileIn(Compile)

  def closureSettings: Seq[Setting[_]] =
    closureSettingsIn(Compile)

  def closureSettingsIn(conf: Configuration): Seq[Setting[_]] =
    closureSettingsManualCompileIn(conf) ++ inConfig(conf)(Seq(
      compile in conf <<= (compile in conf).dependsOn(calcDeps in (conf, closure)),
      cleanJs in (conf, closure) <<= (cleanJs in (conf, closure)).dependsOn(compile in conf),
      closure in conf <<= (closure in conf).dependsOn(cleanJs in (conf, closure)),
      packageBin in conf <<= (packageBin in conf).dependsOn(closure in conf)
    ))


  object ExternsFileFilter extends FileFilter {
    val p = Pattern.compile("^.*/externs/[^/]*\\.js$", Pattern.CASE_INSENSITIVE)

    def accept(path: File): Boolean = {
      p.matcher(path.getCanonicalPath).matches()
    }
  }

  def closureSettings0: Seq[Setting[_]] = Seq(
    charset in closure := Charset.forName("utf-8"),
    jsPrettyPrint in closure := false,
    closureOptions in closure <<= closureOptionsSetting,
    includeFilter in closure := "*.js",
    excludeFilter in closure := (".*" - ".") || HiddenFileFilter || ExternsFileFilter,
    externsIncludeFilter in closure := ExternsFileFilter,
    externsExcludeFilter in closure := (".*" - ".") || HiddenFileFilter,
    entryFilter in closure := "*.entry.js",
    includeFilter in calcDeps := "*.js",
    excludeFilter in calcDeps := (".*" - ".") || HiddenFileFilter || ExternsFileFilter,
    suffix in closure := "",
    unmanagedSources in closure <<= closureSourcesTask,
    unmanagedSources in calcDeps <<= calcDepsSourcesTask,
    clean in closure <<= closureCleanTask,
    clean in calcDeps <<= calcDepsCleanTask,
    cleanJs in closure <<= calcDepsCleanTask,
    closure <<= closureCompilerTask,
    calcDeps in closure <<= calcDepsTask,
    externsFiles in closure <<= externsFilesTask,
    entryFiles in closure <<= entryFilesTask,
    jsVariableRenamingPolicy in closure := VariableRenamingPolicy.LOCAL,
    jsPrettyPrint in closure := false,
    jsStrictMode in closure := false,
    jsCompilationLevel in closure := CompilationLevel.SIMPLE_OPTIMIZATIONS,
    jsWarningLevel in closure := WarningLevel.DEFAULT,
    jsGenerateMapFile in closure := false
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
      unmanagedSources in closure,
      externsFiles in closure,
      entryFiles in closure,
      closureOptions in closure,
      suffix in closure,
      jsGenerateMapFile in closure) map {
      (out,
       sourceDir,
       outDir,
       sourceFiles,
       externsFiles,
       entryFiles,
       options,
       suffix,
       generateMapFile) => {
        (for {
          entryFile <- entryFiles
          mapping = new JsSourceMapping(sourceDir, entryFile, outDir, sourceFiles, suffix)
          if mapping.changed
        } yield {
          (mapping.entryFile, mapping.outFile)
        }) match {
          case Nil =>
            out.log.debug("No JavaScript entry files to compile")
          case xs =>
            out.log.info("Compiling %d entry files to %s" format(xs.size, outDir))
            xs map doCompile(out.log, options, sourceFiles, externsFiles, generateMapFile)
            out.log.debug("Compiled %s entry files" format xs.size)
        }
        compiled(outDir)
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

  private def externsFilesTask =
    (sourceDirectory in closure, externsIncludeFilter in closure, externsExcludeFilter in closure) map {
      (sourceDir, incl, excl) =>
        sourceDir.descendantsExcept(incl, excl).get
    }

  private def entryFilesTask =
    (sourceDirectory in closure, entryFilter in closure, excludeFilter in closure) map {
      (sourceDir, incl, excl) =>
        sourceDir.descendantsExcept(incl, excl).get
    }

  private def doCompile(log: Logger, options: CompilerOptions, src: Seq[File], externs: Seq[File], generateMapFile: Boolean)(pair: (File, File)) = {
    val (entry, out) = pair
    log.debug("Compiling %s" format entry)
    val compiler = new Compiler(options)
    compiler.compile(GoogDeps.filterByDependencies(entry, src), externs, out, log, generateMapFile)
  }

  private def compiled(under: File) = (under ** "*.js").get

}
