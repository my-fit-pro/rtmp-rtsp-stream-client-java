package com.pedro.rtmp.rtmp.message

import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.utils.readUInt32
import com.pedro.rtmp.utils.writeUInt32
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Created by pedro on 21/04/21.
 */
class SetChunkSize(private var chunkSize: Int = 128):
    RtmpMessage(BasicHeader(ChunkType.TYPE_0, ChunkStreamId.PROTOCOL_CONTROL)) {

  override fun readBody(input: InputStream) {
    chunkSize = input.readUInt32()
  }

  override fun storeBody(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    byteArrayOutputStream.writeUInt32(chunkSize)
    return byteArrayOutputStream.toByteArray()
  }

  override fun getType(): MessageType = MessageType.SET_CHUNK_SIZE

  override fun getSize(): Int = 4

  override fun toString(): String {
    return "SetChunkSize(chunkSize=$chunkSize)"
  }
}