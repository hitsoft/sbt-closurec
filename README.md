# sbt-closurec

Google Closure Compiler plugin for SBT

This plugin is helping to work with Google Closure Compiler of your JavaScripts during development and packaging.

## Install

```scala

val verClosurec = "0.1.3"

lazy val closurecPlugin = uri(s"https://github.com/hitsoft/sbt-closurec.git#$verClosurec")

lazy val root = project.in(file("."))
	.dependsOn(closurecPlugin)
```

_NOTE_ this plugin is targeting the release of sbt, 0.13.7

You will need to add the following to your `project/build.properties` file if you have multiple versions of sbt installed

    sbt.version=0.13.7

Be sure to use the [latest launcher](http://www.scala-sbt.org/0.13.0/docs/Getting-Started/Setup.html#installing-sbt).

## Usage

### Out of the box

Add the following to your `build.sbt` file

```scala
seq(closureSettings:_*)
```

Default configuration suppose you have following files structure

`src/main/javascript` - the place where your JavaScript files will be looked for

#### In Developer mode

This mode suppose you do just `compile` action and use xsbt-web plugin to run Jetty with your webapp.

In this case plugin will copy all your `src/main/javascript` .js files to the `target/resource-managed/js` and create the deps.js file according to your goog.require and goog.provide.

In this mode you should make reference in your html to `js/deps.js` and your root js file like `js/app.entry.js`

_SUMMARY_ In developer mode no real closure compilation performed, plugin just refreshes deps.js file and you work with raw sources js files

#### In Package mode

In this mode SIMPLE_OPTIMIZATIONS google closure compilation will be used. This will be called with `package` action.

How it supposed to work:

Plugin searches for the `*.entry.js` files. Those files will be compiled with dependencies using goog.provide, goog.require functions and packaged to `js/*.min.js` files. For example if you have `app.entry.js` in `src/main/javascript` you will have `js/app.min.js` in your final webapp packaged folder.

Externs are searched in `externs/*.js` anywhere inside your `src/main/javascript` folder.

_SUMMARY_ In Package mode (when you call `package` in sbt), your JavaScripts will be compiled with google closure compiler and packaged to WAR without sources, just compiled version.

## Customization

Here is the list of options (keys) that can be adjusted in your `build.sbt` script

```scala
// Turn on ADVANCED optimizations instead of SIMPLE
(ClosureKeys.compilationLevel in Compile) := CompilationLevel.ADVANCED_OPTIMIZATIONS

// Turn on JavaScript strict mode
(ClosureKeys.strictMode in Compile) := true

// Turn on pretty printing of compiled JavaScript
(ClosureKeys.prettyPrint in Compile) := true

// Specify the compiled file name suffix. app.entry.js -> app-suffix.min.js
(ClosureKeys.suffix in Compile) := "suffix"
```
