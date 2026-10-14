package com.github.tototoshi.dbcache.play

import com.github.tototoshi.dbcache.{CacheDatabase, CacheEntry, DBCache}
import com.github.tototoshi.dbcache.play.CacheResult.Done
import org.scalatest.funsuite.AnyFunSuite
import play.api.cache.{AsyncCacheApi, SyncCacheApi}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DBCacheApiTest extends AnyFunSuite {

  private def database(): DBCache = {
    val entries = TrieMap.empty[String, CacheEntry]
    new DBCache(new CacheDatabase {
      override def set(entry: CacheEntry): Unit = {
        entries.put(entry.key, entry)
      }
      override def get(key: String): Option[CacheEntry] =
        entries.get(key).filter(_.expiration.forall(_ > System.currentTimeMillis()))
      override def remove(key: String): Unit = {
        entries.remove(key)
      }
    }) {}
  }

  test("synchronous cache supports default expiration, lookup, and removal") {
    val cache: SyncCacheApi = new DBCacheApi(database())
    assert(cache.get[String]("key").isEmpty)
    cache.set("key", "value")
    assert(cache.get[String]("key").contains("value"))
    cache.remove("key")
    assert(cache.get[String]("key").isEmpty)
  }

  test("synchronous cache evaluates a default only on a miss") {
    val cache: SyncCacheApi = new DBCacheApi(database())
    assert(cache.getOrElseUpdate("key")("value") == "value")
    assert(cache.getOrElseUpdate[String]("key")(fail("cached value was ignored")) == "value")
  }

  test("explicit expiration is passed to the database") {
    val cache: SyncCacheApi = new DBCacheApi(database())
    cache.set("key", "value", Duration.Zero)
    assert(cache.get[String]("key").isEmpty)
  }

  test("asynchronous cache returns the framework Done and shares its synchronous view") {
    val cache: AsyncCacheApi = new AsyncDBCacheApi(database())
    assert(Await.result(cache.set("key", "value"), 5.seconds) == Done)
    assert(Await.result(cache.get[String]("key"), 5.seconds).contains("value"))
    assert(cache.sync.get[String]("key").contains("value"))
    assert(Await.result(cache.remove("key"), 5.seconds) == Done)
    assert(Await.result(cache.get[String]("key"), 5.seconds).isEmpty)
  }

  test("asynchronous cache evaluates a default only on a miss") {
    val cache: AsyncCacheApi = new AsyncDBCacheApi(database())
    assert(Await.result(cache.getOrElseUpdate("missing")(Future.successful("default")), 5.seconds) == "default")
    cache.sync.set("key", "value")
    assert(Await.result(cache.getOrElseUpdate[String]("key")(fail("cached value was ignored")), 5.seconds) == "value")
  }

  test("removing all entries remains unsupported") {
    val cache: AsyncCacheApi = new AsyncDBCacheApi(database())
    intercept[UnsupportedOperationException](cache.removeAll())
  }
}
