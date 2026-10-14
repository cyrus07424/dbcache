import sbt._
import Keys._

object Publish {

  private val url = "https://github.com/tototoshi/dbcache"

  private object License {
    val name = "Apache License, Version 2.0"
    val url = "https://www.apache.org/licenses/LICENSE-2.0.html"
    val distribution = "repo"
  }

  private object SCM {
    val url = "git@github.com:tototoshi/dbcache.git"
    val connection = "scm:git:git@github.com:tototoshi/dbcache.git"
  }

  private object Developer {
    val id = "tototoshi"
    val name = "Toshiyuki Takahashi"
    val url = "https://tototoshi.github.io"
  }

  private val _pomExtra =
    <url>{ url }</url>
    <licenses>
      <license>
        <name>{ License.name }</name>
        <url>{ License.url }</url>
        <distribution>{ License.distribution }</distribution>
      </license>
    </licenses>
    <scm>
      <url>{ SCM.url }</url>
      <connection>{ SCM.connection }</connection>
    </scm>
    <developers>
      <developer>
        <id>{ Developer.id }</id>
        <name>{ Developer.name }</name>
        <url>{ Developer.url }</url>
      </developer>
    </developers>

  private val repository = sys.env.getOrElse("GITHUB_REPOSITORY", "cyrus07424/dbcache")

  private val githubPackages = Some("GitHub Packages" at s"https://maven.pkg.github.com/$repository")

  val settings = Seq(
    publishMavenStyle := true,
    publishTo := githubPackages,
    credentials ++= (for {
      user <- sys.env.get("GITHUB_ACTOR").toSeq
      token <- sys.env.get("GITHUB_TOKEN").toSeq
    } yield Credentials("GitHub Package Registry", "maven.pkg.github.com", user, token)),
    Test / publishArtifact := false,
    pomExtra := _pomExtra
  )

  val nonPublishSettings = Seq(
    publishArtifact := false,
    publish := {},
    publish / skip := true,
    publishTo := githubPackages,
    publishLocal := {}
  )

}
