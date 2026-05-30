/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.r2dbc

import java.time.{ Duration => JDuration }
import java.util.Locale

import scala.concurrent.duration._
import scala.jdk.DurationConverters._
import scala.util.hashing.MurmurHash3

import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.persistence.r2dbc.Dialect

object R2dbcProjectionSettings {

  val DefaultConfigPath = "pekko.projection.r2dbc"

  def apply(config: Config): R2dbcProjectionSettings = {
    val logDbCallsExceeding: FiniteDuration =
      config.getString("log-db-calls-exceeding").toLowerCase(Locale.ROOT) match {
        case "off" => -1.millis
        case _     => config.getDuration("log-db-calls-exceeding").toScala
      }

    new R2dbcProjectionSettings(
      dialect = Dialect.fromString(config.getString("dialect")),
      schema = Option(config.getString("offset-store.schema")).filterNot(_.trim.isEmpty),
      offsetTable = config.getString("offset-store.offset-table"),
      timestampOffsetTable = config.getString("offset-store.timestamp-offset-table"),
      managementTable = config.getString("offset-store.management-table"),
      useConnectionFactory = config.getString("use-connection-factory"),
      timeWindow = config.getDuration("offset-store.time-window"),
      keepNumberOfEntries = config.getInt("offset-store.keep-number-of-entries"),
      evictInterval = config.getDuration("offset-store.evict-interval"),
      deleteInterval = config.getDuration("offset-store.delete-interval"),
      logDbCallsExceeding,
      warnAboutFilteredEventsInFlow = config.getBoolean("warn-about-filtered-events-in-flow"),
      offsetBatchSize = config.getInt("offset-store.offset-batch-size")
    )
  }

  /**
   * Java API
   * @since 2.0.0
   */
  def create(config: Config): R2dbcProjectionSettings =
    apply(config)

  /**
   * Java API: Load configuration from `pekko.projection.r2dbc`.
   * @since 2.0.0
   */
  def create(system: ActorSystem[_]): R2dbcProjectionSettings =
    apply(system)

  /**
   * Scala API: Load configuration from `pekko.projection.r2dbc`.
   */
  def apply(system: ActorSystem[_]): R2dbcProjectionSettings =
    apply(system.settings.config.getConfig(DefaultConfigPath))

  /** @since 2.0.0 */
  def apply(config: Config, system: ActorSystem[_]): R2dbcProjectionSettings =
    apply(config.withFallback(system.settings.config.getConfig(DefaultConfigPath)))

  def apply(
      schema: Option[String],
      offsetTable: String,
      timestampOffsetTable: String,
      managementTable: String,
      useConnectionFactory: String,
      timeWindow: JDuration,
      keepNumberOfEntries: Int,
      evictInterval: JDuration,
      deleteInterval: JDuration,
      logDbCallsExceeding: FiniteDuration,
      warnAboutFilteredEventsInFlow: Boolean = true
  ): R2dbcProjectionSettings = new R2dbcProjectionSettings(
    Dialect.Postgres,
    schema,
    offsetTable,
    timestampOffsetTable,
    managementTable,
    useConnectionFactory,
    timeWindow,
    keepNumberOfEntries,
    evictInterval,
    deleteInterval,
    logDbCallsExceeding,
    warnAboutFilteredEventsInFlow,
    offsetBatchSize = 10
  )
}

final class R2dbcProjectionSettings private (
    val dialect: Dialect,
    val schema: Option[String],
    val offsetTable: String,
    val timestampOffsetTable: String,
    val managementTable: String,
    val useConnectionFactory: String,
    val timeWindow: JDuration,
    val keepNumberOfEntries: Int,
    val evictInterval: JDuration,
    val deleteInterval: JDuration,
    val logDbCallsExceeding: FiniteDuration,
    val warnAboutFilteredEventsInFlow: Boolean,
    val offsetBatchSize: Int
) extends Serializable {

  override def toString: String =
    s"R2dbcProjectionSettings($dialect, $schema, $offsetTable, $timestampOffsetTable, $managementTable, " +
    s"$useConnectionFactory, $timeWindow, $keepNumberOfEntries, $evictInterval, $deleteInterval, $logDbCallsExceeding, $warnAboutFilteredEventsInFlow, $offsetBatchSize)"

  override def equals(other: Any): Boolean =
    other match {
      case that: R2dbcProjectionSettings =>
        dialect == that.dialect && schema == that.schema &&
        offsetTable == that.offsetTable && timestampOffsetTable == that.timestampOffsetTable &&
        managementTable == that.managementTable && useConnectionFactory == that.useConnectionFactory &&
        timeWindow == that.timeWindow && keepNumberOfEntries == that.keepNumberOfEntries &&
        evictInterval == that.evictInterval && deleteInterval == that.deleteInterval &&
        logDbCallsExceeding == that.logDbCallsExceeding &&
        warnAboutFilteredEventsInFlow == that.warnAboutFilteredEventsInFlow &&
        offsetBatchSize == that.offsetBatchSize
      case _ => false
    }

  override def hashCode(): Int = {
    val values = Seq[Any](
      dialect,
      schema,
      offsetTable,
      timestampOffsetTable,
      managementTable,
      useConnectionFactory,
      timeWindow,
      keepNumberOfEntries,
      evictInterval,
      deleteInterval,
      logDbCallsExceeding,
      warnAboutFilteredEventsInFlow,
      offsetBatchSize
    )
    val h = values.foldLeft(MurmurHash3.productSeed) { case (h, value) =>
      MurmurHash3.mix(h, value.##)
    }
    MurmurHash3.finalizeHash(h, values.size)
  }

  private[this] def copy(
      dialect: Dialect = dialect,
      schema: Option[String] = schema,
      offsetTable: String = offsetTable,
      timestampOffsetTable: String = timestampOffsetTable,
      managementTable: String = managementTable,
      useConnectionFactory: String = useConnectionFactory,
      timeWindow: JDuration = timeWindow,
      keepNumberOfEntries: Int = keepNumberOfEntries,
      evictInterval: JDuration = evictInterval,
      deleteInterval: JDuration = deleteInterval,
      logDbCallsExceeding: FiniteDuration = logDbCallsExceeding,
      warnAboutFilteredEventsInFlow: Boolean = warnAboutFilteredEventsInFlow,
      offsetBatchSize: Int = offsetBatchSize
  ): R2dbcProjectionSettings =
    new R2dbcProjectionSettings(
      dialect,
      schema,
      offsetTable,
      timestampOffsetTable,
      managementTable,
      useConnectionFactory,
      timeWindow,
      keepNumberOfEntries,
      evictInterval,
      deleteInterval,
      logDbCallsExceeding,
      warnAboutFilteredEventsInFlow,
      offsetBatchSize
    )

  def withDialect(dialect: Dialect): R2dbcProjectionSettings =
    copy(dialect = dialect)

  def withSchema(schema: Option[String]): R2dbcProjectionSettings =
    copy(schema = schema)

  def withOffsetTable(offsetTable: String): R2dbcProjectionSettings =
    copy(offsetTable = offsetTable)

  def withTimestampOffsetTable(timestampOffsetTable: String): R2dbcProjectionSettings =
    copy(timestampOffsetTable = timestampOffsetTable)

  def withManagementTable(managementTable: String): R2dbcProjectionSettings =
    copy(managementTable = managementTable)

  def withUseConnectionFactory(useConnectionFactory: String): R2dbcProjectionSettings =
    copy(useConnectionFactory = useConnectionFactory)

  def withTimeWindow(timeWindow: JDuration): R2dbcProjectionSettings =
    copy(timeWindow = timeWindow)

  def withKeepNumberOfEntries(keepNumberOfEntries: Int): R2dbcProjectionSettings =
    copy(keepNumberOfEntries = keepNumberOfEntries)

  def withEvictInterval(evictInterval: JDuration): R2dbcProjectionSettings =
    copy(evictInterval = evictInterval)

  def withDeleteInterval(deleteInterval: JDuration): R2dbcProjectionSettings =
    copy(deleteInterval = deleteInterval)

  def withLogDbCallsExceeding(logDbCallsExceeding: FiniteDuration): R2dbcProjectionSettings =
    copy(logDbCallsExceeding = logDbCallsExceeding)

  def withWarnAboutFilteredEventsInFlow(warnAboutFilteredEventsInFlow: Boolean): R2dbcProjectionSettings =
    copy(warnAboutFilteredEventsInFlow = warnAboutFilteredEventsInFlow)

  /** @since 2.0.0 */
  def withOffsetBatchSize(offsetBatchSize: Int): R2dbcProjectionSettings =
    copy(offsetBatchSize = offsetBatchSize)

  val offsetTableWithSchema: String = schema.map(_ + ".").getOrElse("") + offsetTable
  val timestampOffsetTableWithSchema: String = schema.map(_ + ".").getOrElse("") + timestampOffsetTable
  val managementTableWithSchema: String = schema.map(_ + ".").getOrElse("") + managementTable

  def isOffsetTableDefined: Boolean = offsetTable.nonEmpty
}
