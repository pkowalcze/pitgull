import com.typesafe.sbt.packager.docker.ExecCmd
import com.typesafe.sbt.packager.docker.Cmd

inThisBuild(
  List(
    organization := "io.pg",
    homepage := Some(url("https://github.com/pitgull/pitgull")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "kubukoz",
        "Jakub Kozłowski",
        "kubukoz@gmail.com",
        url("https://blog.kubukoz.com")
      )
    )
  )
)

val GraalVM11 = "graalvm-ce-java11@20.1.0"

ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / githubWorkflowJavaVersions := Seq(GraalVM11)
ThisBuild / githubWorkflowPublishTargetBranches := Nil

// todo: reenable missinglink
//ThisBuild / githubWorkflowBuild := List(
//  WorkflowStep.Sbt(List("test", "missinglinkCheck"))
//)

Test / fork := true

missinglinkExcludedDependencies in ThisBuild += moduleFilter(
  organization = "org.slf4j",
  name = "slf4j-api"
)

def crossPlugin(x: sbt.librarymanagement.ModuleID) =
  compilerPlugin(x.cross(CrossVersion.full))

val compilerPlugins = List(
  crossPlugin("org.typelevel" % "kind-projector" % "0.11.3"),
  crossPlugin("com.github.cb372" % "scala-typed-holes" % "0.1.7"),
  crossPlugin("com.kubukoz" % "better-tostring" % "0.2.6"),
  compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

val Scala213 = "2.13.4"

val commonSettings = List(
  scalaVersion := Scala213,
  scalacOptions --= List("-Xfatal-warnings"),
  scalacOptions += "-Ymacro-annotations",
  libraryDependencies ++= List(
    "org.typelevel" %% "cats-core" % "2.3.1",
    "org.typelevel" %% "cats-effect" % "2.3.1",
    "org.typelevel" %% "cats-tagless-macros" % "0.12",
    "co.fs2" %% "fs2-core" % "2.5.0",
    "com.github.valskalla" %% "odin-core" % "0.9.1",
    "io.circe" %% "circe-core" % "0.13.0",
    "com.github.julien-truffaut" %% "monocle-macro" % "2.1.0",
    "com.disneystreaming" %% "weaver-framework" % "0.5.1" % Test,
    "com.disneystreaming" %% "weaver-scalacheck" % "0.5.1" % Test
  ) ++ compilerPlugins,
  testFrameworks += new TestFramework("weaver.framework.TestFramework"),
  skip in publish := true
)

lazy val gitlab = project
  .settings(
    commonSettings,
    libraryDependencies ++= List(
      "is.cir" %% "ciris" % "1.2.1",
      "com.kubukoz" %% "caliban-gitlab" % "0.0.9",
      "io.circe" %% "circe-generic-extras" % "0.13.0",
      "io.circe" %% "circe-parser" % "0.13.0" % Test,
      "io.circe" %% "circe-literal" % "0.13.0" % Test,
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "0.17.7",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "0.17.7",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "0.17.7"
    )
  )
  .dependsOn(core)

lazy val core = project.settings(commonSettings).settings(name += "-core")

//workaround for docker not accepting + (the default separator in sbt-dynver)
dynverSeparator in ThisBuild := "-"

val installDhallJson =
  ExecCmd(
    "RUN",
    "sh",
    "-c",
    "curl -L https://github.com/dhall-lang/dhall-haskell/releases/download/1.34.0/dhall-json-1.7.1-x86_64-linux.tar.bz2 | tar -vxj -C /"
  )

val dockerBuild = taskKey[String]("Build docker image")

val dockerPush = taskKey[String]("Build and publish docker image")

lazy val pitgull =
  project
    .in(file("."))
    .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
    .settings(commonSettings)
    .settings(
      name := "pitgull",
      mainClass := Some("io.pg.ProjectConfigReader"),
      buildInfoPackage := "io.pg",
      buildInfoKeys := List(version, scalaVersion),
      libraryDependencies ++= List(
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "0.17.7",
        "com.softwaremill.sttp.client" %% "http4s-backend" % "2.2.9",
        "com.softwaremill.sttp.client3" %% "http4s-backend" % "3.0.0",
        "org.http4s" %% "http4s-blaze-server" % "0.21.15",
        "org.http4s" %% "http4s-blaze-client" % "0.21.15",
        "is.cir" %% "ciris" % "1.2.1",
        "io.circe" %% "circe-generic-extras" % "0.13.0",
        "io.estatico" %% "newtype" % "0.4.4",
        "io.scalaland" %% "chimney" % "0.6.1",
        "org.typelevel" %% "cats-mtl-core" % "0.7.1",
        "io.chrisdavenport" %% "cats-time" % "0.3.4",
        "com.github.valskalla" %% "odin-core" % "0.9.1",
        "com.github.valskalla" %% "odin-slf4j" % "0.9.1",
        "io.github.vigoo" %% "prox" % "0.5.2"
      ),
      dockerBuild := {
        val target = stage.value

        import scala.sys.process._
        s"docker build --quiet --file Dockerfile $target".!!.trim
      },
      dockerPush := {
        val pattern = "sha256\\:(.+)".r
        val sha = dockerBuild.value match {
          case pattern(hash) => hash
        }

        val tag = s"kubukoz/pitgull:${version.value}"

        import scala.sys.process._
        s"docker tag $sha $tag".!
        s"docker push $tag".!

        tag
      }
    )
    .dependsOn(core, gitlab)
    .aggregate(core, gitlab)
