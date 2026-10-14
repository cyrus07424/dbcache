# dbcache

![Scala CI](https://github.com/tototoshi/dbcache/workflows/Scala%20CI/badge.svg)

RDB as a cache storage

## Install

Artifacts are published to GitHub Packages. Add the resolver and credentials to
your application's `build.sbt` (authentication is required even for public packages):

```scala
resolvers += "dbcache GitHub Packages" at "https://maven.pkg.github.com/cyrus07424/dbcache"
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env("GITHUB_ACTOR"),
  sys.env("GITHUB_TOKEN")
)
```

For local installation, set `GITHUB_ACTOR` to your GitHub username and
`GITHUB_TOKEN` to a personal access token (classic) with `read:packages` permission
and access to this repository's packages. Do not commit the token.

```scala
libraryDependencies += "com.github.tototoshi" %% "dbcache-mysql" % "1.0.0"

// or
// libraryDependencies += "com.github.tototoshi" %% "dbcache-postgresql" % "1.0.0"
```

## Setup

dbcache is a cache library which uses RDB (mysql and postgresql) as backend. it uses a table named `cache_entries` which has columns named `key`, `value`, `expired_at` and `created_at` like below.


```sql
-- mysql
CREATE TABLE `cache_entries` (
  `cache_key` varchar(191) PRIMARY KEY,
  `cache_value` mediumblob NOT NULL,
  `expired_at` datetime,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  INDEX (`expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4

-- postgresql
CREATE TABLE cache_entries (
  cache_key varchar(191) PRIMARY KEY,
  cache_value bytea NOT NULL,
  expired_at timestamp,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL
);

create index on cache_entries(expired_at);
```

You can add more constraint and index (for example: expired_at as NOT NULL).


```scala
package example

import java.sql.{Connection, DriverManager}
import java.util.Date

import com.github.tototoshi.dbcache.mysql.MySQLCache
import com.github.tototoshi.dbcache.{ConnectionFactory, DBCache}

import scala.concurrent.duration.Duration

case class User(id: Int, name: String)

object Example {

  class ExampleConnectionFactory extends ConnectionFactory {

    val driver   = "com.mysql.cj.jdbc.Driver"
    val url      = "jdbc:mysql//localhost/mydb"
    val user     = "user"
    val password = "password"

    Class.forName(driver)

    override def get(): Connection = {
      DriverManager.getConnection(url, user, password)
    }
  }

  def main(args: Array[String]): Unit =  {

    val cache: DBCache = new MySQLCache(new ExampleConnectionFactory)

    cache.set("key", User(1, "John"), Duration("5s"))

    1.to(10).foreach { _ =>
      val user = cache.get[User]("key")
      val date = new Date()
      println(s"$date: $user")
      Thread.sleep(1000)
    }

  }

}


/* output
Tue Apr 26 22:26:48 JST 2016: Some(User(1,John))
Tue Apr 26 22:26:49 JST 2016: Some(User(1,John))
Tue Apr 26 22:26:50 JST 2016: Some(User(1,John))
Tue Apr 26 22:26:51 JST 2016: Some(User(1,John))
Tue Apr 26 22:26:52 JST 2016: Some(User(1,John))
Tue Apr 26 22:26:53 JST 2016: None
Tue Apr 26 22:26:54 JST 2016: None
Tue Apr 26 22:26:55 JST 2016: None
Tue Apr 26 22:26:56 JST 2016: None
Tue Apr 26 22:26:57 JST 2016: None
*/
```


## With Play framework

|dbcache-play version|Play version|Scala version|
|-------------------:|-----------:|------------:|
|1.0.0               |3.0.x       |2.13/3.3     |
|0.5.0               |2.9.x       |2.13/3.3     |
|0.4.0               |2.8.x       |2.12/2.13    |

For Play **2.6**, use the separate `dbcache-play26` artifact (Play 2.6.25,
Scala **2.12**). The core, MySQL, and PostgreSQL artifacts also support Scala 2.12.
Do not include `dbcache-play` in a Play 2.6 application: it targets Play 3 and Pekko,
whereas `dbcache-play26` uses Akka. Both expose the same
`com.github.tototoshi.dbcache.play` API.

```scala
libraryDependencies ++= Seq(
  "com.github.tototoshi" %% "dbcache-play26" % "1.0.0",
  "com.github.tototoshi" %% "dbcache-mysql" % "1.0.0"
)
```

Play 2.6 is end-of-life; this compatibility artifact does not provide security
updates for the framework itself.


Add dbcache-play to dependencies.

```scala
libraryDependencies += "com.github.tototoshi" %% "dbcache-play" % "1.0.0"
```

The code below is Play integration example with scalikejdbc

```scala
import java.sql.Connection

import com.github.tototoshi.dbcache.ConnectionFactory
import com.github.tototoshi.dbcache.mysql.MySQLCache
import com.github.tototoshi.dbcache.play.DBCacheApi
import javax.inject.{Inject, Provider}
import play.api.Environment
import play.api.cache.SyncCacheApi
import scalikejdbc.DB

class MySQLCacheApiProvider @Inject()(environment: Environment) extends Provider[SyncCacheApi] {

  private val connectionFactory = new ConnectionFactory {
    override def get(): Connection = {
      DB.autoCommitSession().connection
    }
  }
  override def get(): SyncCacheApi = {
    val mysqlCache = new MySQLCache(connectionFactory, environment.classLoader)
    new DBCacheApi(mysqlCache)
  }
}
```

Disable EhCache which is the default cache backend of Play.

```
play.modules.disabled += "play.api.cache.ehcache.EhCacheModule"
```

## Publishing

Pushing a version tag such as `v1.0.1` runs the GitHub Actions publishing workflow.
It runs `sbt +test` with MySQL and PostgreSQL, then publishes all library artifacts
to the current repository's GitHub Packages registry with `sbt +publish`.
The tag without its leading `v` becomes the artifact version; use a new version
for each release. Root and example projects are not published.

Publishing uses the workflow's `GITHUB_TOKEN` with `packages:write`; no personal
access token is needed for releases. Ensure repository/organization settings allow
the workflow to write packages. In another repository's Actions workflow, grant
that repository access to the packages and use a token with `packages:read`.
