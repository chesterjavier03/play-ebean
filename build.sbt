import sbt.inc.Analysis

val PlayVersion = "2.4-2014-11-04-10ce984-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .aggregate(core)
  .settings(common: _*)
  .settings(crossScala: _*)
  .settings(noPublish: _*)

lazy val core = project
  .in(file("play-ebean"))
  .settings(common: _*)
  .settings(crossScala: _*)
  .settings(publishMaven: _*)
  .settings(playdocSettings: _*)
  .settings(
    name := "play-ebean",
    projectID := withSourceUrl.value,
    libraryDependencies ++= playEbeanDeps,
    compile in Compile := enhanceEbeanClasses(
      (dependencyClasspath in Compile).value,
      (compile in Compile).value,
      (classDirectory in Compile).value,
      "play/db/ebean/**"
    )
  )

lazy val plugin = project
  .in(file("sbt-play-ebean"))
  .settings(common: _*)
  .settings(scriptedSettings: _*)
  .settings(publishSbtPlugin: _*)
  .settings(
    name := "sbt-play-ebean",
    organization := "com.typesafe.sbt",
    sbtPlugin := true,
    libraryDependencies ++= sbtPlayEbeanDeps,
    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % PlayVersion),
    resourceGenerators in Compile <+= generateVersionFile,
    scriptedLaunchOpts ++= Seq("-Dplay-ebean.version=" + version.value),
    scriptedDependencies := {
      val () = publishLocal.value
      val () = (publishLocal in core).value
    }
  )

// Shared settings

def common: Seq[Setting[_]] = Seq(
  organization := "com.typesafe.play",
  version := "1.0-SNAPSHOT",
  scalaVersion := sys.props.get("scala.version").getOrElse("2.10.4"),
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
  (javacOptions in compile) := Seq("-source", "1.7", "-target", "1.7"),
  (javacOptions in doc) := Seq("-source", "1.7"),
  resolvers ++= DefaultOptions.resolvers(snapshot = true),
  resolvers += Resolver.typesafeRepo("releases")
)

def crossScala: Seq[Setting[_]] = Seq(
  crossScalaVersions := Seq("2.10.4", "2.11.1")
)

def publishMaven: Seq[Setting[_]] = Seq(
  publishTo := {
    if (isSnapshot.value) Some(Opts.resolver.sonatypeSnapshots)
    else Some(Opts.resolver.sonatypeStaging)
  },
  homepage := Some(url("https://github.com/playframework/play-ebean")),
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  pomExtra := {
    <scm>
      <url>https://github.com/playframework/play-ebean</url>
      <connection>scm:git:git@github.com:playframework/play-ebean.git</connection>
    </scm>
    <developers>
      <developer>
        <id>playframework</id>
        <name>Play Framework Team</name>
        <url>https://github.com/playframework</url>
      </developer>
    </developers>
  },
  pomIncludeRepository := { _ => false }
)

def publishSbtPlugin: Seq[Setting[_]] = Seq(
  publishMavenStyle := false,
  publishTo := {
    if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
    else Some(Classpaths.sbtPluginReleases)
  }
)

def noPublish: Seq[Setting[_]] = Seq(
  publish := {},
  publishLocal := {},
  publishTo := Some(Resolver.file("no-publish", crossTarget.value / "no-publish"))
)

// Dependencies

def playEbeanDeps = Seq(
  "com.typesafe.play" %% "play-java-jdbc" % PlayVersion,
  "org.avaje.ebeanorm" % "avaje-ebeanorm" % "4.2.0",
  avajeEbeanormAgent
)

def sbtPlayEbeanDeps = Seq(
  avajeEbeanormAgent,
  "com.typesafe" % "config" % "1.2.1"
)

def avajeEbeanormAgent = "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % "4.1.10"

// Aggregated documentation

def withSourceUrl = Def.setting {
  val baseUrl = "https://github.com/playframework/play-ebean"
  val sourceTree = if (isSnapshot.value) "master" else "v" + version.value
  val sourceDirectory = IO.relativize((baseDirectory in ThisBuild).value, baseDirectory.value).getOrElse("")
  val sourceUrl = s"$baseUrl/tree/$sourceTree/$sourceDirectory"
  projectID.value.extra("info.sourceUrl" -> sourceUrl)
}

val packagePlaydoc = TaskKey[File]("package-playdoc", "Package play documentation")

def playdocSettings: Seq[Setting[_]] =
  Defaults.packageTaskSettings(packagePlaydoc, mappings in packagePlaydoc) ++
  Seq(
    mappings in packagePlaydoc := {
      val base = (baseDirectory in ThisBuild).value / "docs"
      (base / "manual").***.get pair relativeTo(base)
    },
    artifactClassifier in packagePlaydoc := Some("playdoc"),
    artifact in packagePlaydoc ~= { _.copy(configurations = Seq(Docs)) }
  ) ++
  addArtifact(artifact in packagePlaydoc, packagePlaydoc)

// Ebean enhancement

def enhanceEbeanClasses(classpath: Classpath, analysis: Analysis, classDirectory: File, pkg: String): Analysis = {
  // Ebean (really hacky sorry)
  val cp = classpath.map(_.data.toURI.toURL).toArray :+ classDirectory.toURI.toURL
  val cl = new java.net.URLClassLoader(cp)
  val t = cl.loadClass("com.avaje.ebean.enhance.agent.Transformer").getConstructor(classOf[Array[URL]], classOf[String]).newInstance(cp, "debug=0").asInstanceOf[AnyRef]
  val ft = cl.loadClass("com.avaje.ebean.enhance.ant.OfflineFileTransform").getConstructor(
    t.getClass, classOf[ClassLoader], classOf[String], classOf[String]
  ).newInstance(t, ClassLoader.getSystemClassLoader, classDirectory.getAbsolutePath, classDirectory.getAbsolutePath).asInstanceOf[AnyRef]
  ft.getClass.getDeclaredMethod("process", classOf[String]).invoke(ft, pkg)
  analysis
}

// Version file

def generateVersionFile = Def.task {
  val version = (Keys.version in core).value
  val file = (resourceManaged in Compile).value / "play-ebean.version.properties"
  val content = s"play-ebean.version=$version"
  IO.write(file, content)
  Seq(file)
}
