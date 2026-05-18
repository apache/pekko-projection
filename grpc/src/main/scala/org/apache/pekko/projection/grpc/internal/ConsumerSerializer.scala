/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.projection.grpc.internal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import scala.annotation.tailrec

import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.cluster.ddata.ORMap
import pekko.cluster.ddata.ORSet
import pekko.cluster.ddata.protobuf.ReplicatedDataSerializer
import pekko.cluster.ddata.protobuf.msg.{ ReplicatedDataMessages => rd }
import pekko.serialization.BaseSerializer
import pekko.serialization.SerializerWithStringManifest
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import scalapb.GeneratedMessage

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class ConsumerSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {
  private val ConsumerFilterStoreStateManifest = "A"
  private val ConsumerFilterKeyManifest = "B"

  private final val CompressionBufferSize = 1024 * 4

  private val replicatedDataSerializer = new ReplicatedDataSerializer(system)

  override def manifest(obj: AnyRef): String = obj match {
    case _: DdataConsumerFilterStore.State             => ConsumerFilterStoreStateManifest
    case _: DdataConsumerFilterStore.ConsumerFilterKey => ConsumerFilterKeyManifest
    case _                                             =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case state: DdataConsumerFilterStore.State           => compress(stateToProto(state))
    case key: DdataConsumerFilterStore.ConsumerFilterKey => replicatedDataSerializer.keyIdToBinary(key.id)
    case _                                               =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case ConsumerFilterStoreStateManifest => stateFromBinary(decompress(bytes))
    case ConsumerFilterKeyManifest        =>
      DdataConsumerFilterStore.ConsumerFilterKey(replicatedDataSerializer.keyIdFromBinary(bytes))
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  private def orsetToBytes(orset: ORSet[_]): ByteString = {
    toProtoByteStringUnsafe(replicatedDataSerializer.orsetToProto(orset).toByteArray)
  }

  private def toProtoByteStringUnsafe(bytes: Array[Byte]): ByteString = {
    if (bytes.isEmpty)
      ByteString.EMPTY
    else {
      UnsafeByteOperations.unsafeWrap(bytes)
    }
  }

  private def orsetFromBinary(bytes: Array[Byte]): ORSet[String] = {
    replicatedDataSerializer.orsetFromProto(rd.ORSet.parseFrom(bytes)).asInstanceOf[ORSet[String]]
  }

  private def stateToProto(state: DdataConsumerFilterStore.State): proto.ConsumerFilterStoreState = {
    val excludeTagsBytes = orsetToBytes(state.excludeTags)
    val includeTagsBytes = orsetToBytes(state.includeTags)
    val excludeRegexEntityIdsBytes = orsetToBytes(state.excludeRegexEntityIds)
    val includeRegexEntityIdsBytes = orsetToBytes(state.includeRegexEntityIds)
    val excludeEntityIdsBytes = orsetToBytes(state.excludeEntityIds)
    val seqNrMap = Some(seqNrMapToProto(state.includeEntityOffsets))

    proto.ConsumerFilterStoreState(
      excludeTagsBytes,
      includeTagsBytes,
      excludeRegexEntityIdsBytes,
      includeRegexEntityIdsBytes,
      excludeEntityIdsBytes,
      seqNrMap)
  }

  private def seqNrMapToProto(seqNrMap: DdataConsumerFilterStore.SeqNrMap): proto.SeqNrMap = {
    val keys = orsetToBytes(seqNrMap.underlying.keys)
    // deterministic order is important
    val entries = seqNrMap.entries.toVector.sortBy(_._1).map {
      case (entityId, seqNr) => proto.SeqNrMap.Entry(entityId, seqNr.nr)
    }
    proto.SeqNrMap(keys, entries)
  }

  private def stateFromBinary(bytes: Array[Byte]): DdataConsumerFilterStore.State = {
    val protoState = proto.ConsumerFilterStoreState.parseFrom(bytes)
    val excludeTags = orsetFromBinary(protoState.excludeTags.toByteArray)
    val includeTags = orsetFromBinary(protoState.includeTags.toByteArray)
    val excludeRegexEntityIds = orsetFromBinary(protoState.excludeRegexEntityIds.toByteArray)
    val includeRegexEntityIds = orsetFromBinary(protoState.includeRegexEntityIds.toByteArray)
    val excludeEntityIds = orsetFromBinary(protoState.excludeEntityIds.toByteArray)
    val includeEntityOffsets = protoState.includeEntityOffsets match {
      case Some(protoSeqNrMap) => seqNrMapFromProto(protoSeqNrMap)
      case None                => DdataConsumerFilterStore.SeqNrMap.empty
    }
    DdataConsumerFilterStore.State(
      excludeTags,
      includeTags,
      excludeRegexEntityIds,
      includeRegexEntityIds,
      excludeEntityIds,
      includeEntityOffsets)
  }

  private def seqNrMapFromProto(protoSeqNrMap: proto.SeqNrMap): DdataConsumerFilterStore.SeqNrMap = {
    val entries = protoSeqNrMap.entries.map {
      case proto.SeqNrMap.Entry(key, seqNr, _) => key -> DdataConsumerFilterStore.SeqNr(seqNr)
    }.toMap
    // FIXME if/when implementing delta crdt the VanillaORMapTag might have to changed?
    val underlying =
      new ORMap(keys = orsetFromBinary(protoSeqNrMap.orsetKeys.toByteArray), entries, ORMap.VanillaORMapTag)
    DdataConsumerFilterStore.SeqNrMap(underlying)
  }

  // copied from Akka
  private def compress(msg: GeneratedMessage): Array[Byte] = {
    val bos = new ByteArrayOutputStream(CompressionBufferSize)
    val zip = new GZIPOutputStream(bos)
    try msg.writeTo(zip)
    finally zip.close()
    bos.toByteArray
  }

  // copied from Akka
  private def decompress(bytes: Array[Byte]): Array[Byte] = {
    val in = new GZIPInputStream(new ByteArrayInputStream(bytes))
    val out = new ByteArrayOutputStream()
    val buffer = new Array[Byte](CompressionBufferSize)

    @tailrec def readChunk(): Unit = in.read(buffer) match {
      case -1 => ()
      case n  =>
        out.write(buffer, 0, n)
        readChunk()
    }

    try readChunk()
    finally in.close()
    out.toByteArray
  }

}
