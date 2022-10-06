package one.kuring

import kotlinx.coroutines.suspendCancellableCoroutine

import java.nio.ByteBuffer

abstract class AbstractFile internal constructor(
    val path: String,
    private val pathAddress: Long,
    val fd: Int,
    private val pollableStatus: PollableStatus,
    internal val executor: EventExecutor
) {


    /**
     * Reads a sequence of bytes from this file into the given buffer.
     *
     * Bytes are read starting at this file current position, and
     * then the file position is updated with the number of bytes actually
     * read.
     *
     * @param buffer The buffer into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     *
     * @return The number of bytes read, possibly zero
     */
    suspend fun read(buffer: ByteBuffer): Int {
        return read(buffer, buffer.limit())
    }

    /**
     *
     * Reads a sequence of bytes from this file into a subsequence of the
     * given buffers.
     *
     * Bytes are read starting at offset, and
     * then the file position is updated with the number of bytes actually
     * read.
     *
     * @param buffers The buffers into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     * @param position The file position at which the transfer is to begin; must be non-negative
     * @param length The maximum number of buffers to be accessed; must be non-negative and no larger than buffers
     *
     * @return The number of bytes read, possibly zero
     */
    suspend fun read(buffers: Array<ByteBuffer>, position: Long, length: Int): Int {
        val iovecArray = IovecArray(buffers)
        val bytesRead = suspendCancellableCoroutine {
            executor.executeCommand(
                Command.readVectored(
                    fd,
                    position,
                    iovecArray.iovecArrayAddress,
                    length,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        iovecArray.updatePositions(bytesRead);
        return bytesRead
    }


    /**
     * Reads a sequence of bytes from this file into the given buffers.
     *
     * Bytes are read starting at this file current position, and
     * then the file position is updated with the number of bytes actually
     * read.
     *
     * @param buffers The buffers into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     *
     * @return The number of bytes read, possibly zero
     */
    suspend fun read(buffers: Array<ByteBuffer>): Int {
        return read(buffers, -1, buffers.size)
    }


    /**
     * Writes a sequence of bytes to this file from the given buffer.
     *
     * Bytes are written starting at this file current position
     * unless the file is in append mode, in which case the position is
     * first advanced to the end of the file. The file is grown, if necessary,
     * to accommodate the written bytes, and then the file position is updated
     * with the number of bytes actually written.
     *
     * @param buffer The buffer from which bytes are to be retrieved. Must be allocated with [ByteBuffer.allocateDirect]
     *
     * @return The number of bytes written, possibly zero
     */
    suspend fun write(buffer: ByteBuffer): Int {
        return write(buffer, buffer.limit())
    }

    /**
     *
     * Writes a sequence of bytes from a subsequence of the
     * given buffers to this file.
     *
     * Bytes are written starting at given position and write up to given length.
     *
     * @param buffers The buffer from which bytes are to be retrieved. Must be allocated with [ByteBuffer.allocateDirect]
     * @param position The file position at which the transfer is to begin; must be non-negative
     * @param length The maximum number of buffers to be accessed; must be non-negative and no larger than buffers
     *
     * @return The number of bytes read, possibly zero
     */
    suspend fun write(buffers: Array<ByteBuffer>, position: Long, length: Int): Int {
        val iovecArray = IovecArray(buffers)
        val bytesWritten = suspendCancellableCoroutine {
            executor.executeCommand(
                Command.writeVectored(
                    fd,
                    position,
                    iovecArray.iovecArrayAddress,
                    length,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        iovecArray.updatePositions(bytesWritten);
        return bytesWritten
    }

    /**
     * Returns the size of a file, in bytes.
     *
     * @return file size in bytes
     */
    suspend fun size(): Long {
        val statxBuffer = MemoryUtils.allocateMemory(StatxUtils.BUF_SIZE.toLong())
        suspendCancellableCoroutine {
            executor.executeCommand(
                Command.size(
                    pathAddress,
                    statxBuffer,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        val fileSize = StatxUtils.getSize(statxBuffer)
        MemoryUtils.freeMemory(statxBuffer)
        return fileSize
    }


    /**
     * Issues fdatasync for the underlying file, instructing the OS to flush all writes to the device,
     * providing durability even if the system crashes or is rebooted.
     */
    suspend fun dataSync(): Int {
        return suspendCancellableCoroutine {
            executor.executeCommand(
                Command.dataSync(
                    fd,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
    }


    /**
     * Reads a sequence of bytes from this file to the given buffer,
     * starting at the given file position.
     * This method works in the same manner as the [AbstractFile.read(ByteBuffer)]
     * method, except that bytes are read starting at
     * the given file position rather than at the file current position.
     *
     * @param buffer The buffer into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     * @param position The file position at which the transfer is to begin; must be non-negative
     *
     * @return The number of bytes written, possibly zero
     */
    suspend fun read(buffer: ByteBuffer, position: Long): Int {
        return read(buffer, position, buffer.limit())
    }

    /**
     * Reads using ring buffer pool.
     * Can only be used if the file was opened with an EventExecutor that was created with the
     * [EventExecutor.Builder.withBufRing] parameter.
     * After processing the result of reading you must call the [BufRingResult.close] method to return the buffer
     * ownership to the kernel.
     * <p>
     * Requires kernel 5.19+
     *
     * @param position The file position at which the transfer is to begin; must be non-negative
     */
    suspend fun readFixedBuffer(position: Long): BufRingResult {
        return suspendCancellableCoroutine {
            executor.executeCommand(
                Command.readProvidedBuf(
                    fd,
                    position,
                    pollableStatus,
                    executor,
                    CoroutineObjectResultProvider.newInstance(it)
                )
            )
        }
    }

    /**
     * Writes a sequence of bytes to this file from the given buffer,
     * starting at the given file position.
     * This method works in the same manner as the [AbstractFile.write(ByteBuffer)]
     * method, except that bytes are written starting at
     * the given file position rather than at the file's current position.
     *
     * @param buffer The buffer from which bytes are to be retrieved. Must be allocated with [ByteBuffer.allocateDirect]
     * @param position The file position at which the transfer is to begin; must be non-negative
     *
     * @return The number of bytes written, possibly zero
     */
    suspend fun write(buffer: ByteBuffer, position: Long): Int {
        return write(buffer, position, buffer.limit())
    }

    /**
     * Reads a sequence of bytes from this file to the given buffer,
     * starting at the given file position and read up to length bytes.
     * This method works in the same manner as the [AbstractFile.read(ByteBuffer)]
     * method, except that bytes are read starting at
     * the given file position rather than at the file current position.
     *
     * @param position The file position at which the transfer is to begin; must be non-negative
     * @param length   The content length; must be non-negative
     * @param buffer   The buffer into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     *
     * @return the number of bytes read
     */
    suspend fun read(buffer: ByteBuffer, position: Long, length: Int): Int {
        if (buffer.capacity() < length) {
            throw IllegalArgumentException("Buffer capacity less then length")
        }
        if (buffer.remaining() == 0) {
            return 0
        }
        val bufPosition = buffer.position()
        val read = suspendCancellableCoroutine {
            executor.executeCommand(
                Command.read(
                    fd,
                    position,
                    length,
                    MemoryUtils.getDirectBufferAddress(buffer) + bufPosition,
                    pollableStatus,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        if (read > 0) {
            buffer.position(bufPosition + read)
        }
        return read
    }

    suspend fun read(buffer: Long, position: Long, length: Int): Int {
        val read = suspendCancellableCoroutine {
            executor.executeCommand(
                Command.read(
                    fd,
                    position,
                    length,
                    buffer,
                    pollableStatus,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        return read
    }


    /**
     * Reads a sequence of bytes from this file to the given buffer,
     * starting at the given file position and read up to length bytes.
     * This method works in the same manner as the [AbstractFile.read(ByteBuffer)]
     * method, except that bytes are read starting at
     * the given file position rather than at the file current position.
     *
     * @param length The content length; must be non-negative
     * @param buffer The buffer in which the bytes are to be read
     *
     * @return the number of bytes read
     */
    suspend fun read(buffer: ByteBuffer, length: Int): Int {
        return read(buffer, -1, length)
    }


    /**
     * Writes the data with in the byte buffer the specified length starting at the given file position.
     * If the given position is greater than the file's current size then the file will be grown to accommodate the new bytes;
     * the values of any bytes between the previous end-of-file and the newly-written bytes are unspecified.
     *
     * @param position The file position at which the transfer is to begin; must be non-negative
     * @param length   The content length; must be non-negative
     * @param buffer   The buffer from which bytes are to be retrieved
     *
     * @return the number of bytes written
     */
    suspend fun write(buffer: ByteBuffer, position: Long, length: Int): Int {
        if (buffer.remaining() == 0) {
            return 0
        }
        val bufPos = buffer.position()
        val written = suspendCancellableCoroutine {
            executor.executeCommand(
                Command.write(
                    fd,
                    position,
                    length,
                    MemoryUtils.getDirectBufferAddress(buffer) + bufPos,
                    pollableStatus,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
        if (written > 0) {
            buffer.position(bufPos + written)
        }
        return written
    }


    /**
     * Writes a sequence of bytes to this file from the given buffer,
     * starting at this file current position
     * unless the file is in append mode, in which case the position is
     * first advanced to the end of the file. The file is grown, if necessary,
     * to accommodate the written bytes, and then the file position is updated
     * with the number of bytes actually written.
     *
     * @param length number of bytes to write
     * @param buffer The buffer from which bytes are to be retrieved
     *
     * @return the number of bytes written
     */
    suspend fun write(buffer: ByteBuffer, length: Int): Int {
        return write(buffer, -1, length)
    }

    /**
     *
     * Writes a sequence of bytes from a subsequence of the
     * given buffers to this file.
     *
     * Bytes are written starting at given position.
     *
     * @param buffers The buffer from which bytes are to be retrieved. Must be allocated with [ByteBuffer.allocateDirect]
     * @param position The file position at which the transfer is to begin; must be non-negative
     *
     * @return The number of bytes read, possibly zero
     */
    suspend fun write(buffers: Array<ByteBuffer>, position: Long): Int {
        return write(buffers, position, buffers.size)
    }

    /**
     * Writes a sequence of bytes from this file into the given buffer.
     *
     * Bytes are read starting at this file current position, and
     * then the file position is updated with the number of bytes actually
     * read.
     *
     * @param buffers The buffer into which bytes are to be transferred. Must be allocated with [ByteBuffer.allocateDirect]
     *
     * @return The number of bytes written, possibly zero
     */
    suspend fun write(buffers: Array<ByteBuffer>): Int {
        return write(buffers, -1)
    }

    /**
     * Pre-allocates space in the filesystem to hold a file at least as big as the size argument from specified offset.
     * After a successful call, subsequent writes into the range
     * specified by offset and len are guaranteed not to fail because of
     * lack of disk space.
     *
     * @param size   bytes to allocate; must be non-negative
     * @param offset start offset; must be non-negative
     */
    suspend fun preAllocate(size: Long, offset: Long): Int {
        return suspendCancellableCoroutine {
            executor.executeCommand(
                Command.preAllocate(
                    fd,
                    size,
                    0,
                    offset,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
    }

    /**
     * Remove this file.
     * The file does not have to be closed to be removed.
     * Removing removes the name from the filesystem but the file will still be accessible for as long as it is open.
     */
    suspend fun remove(): Int {
        return suspendCancellableCoroutine {
            executor.executeCommand(
                Command.unlink(
                    -1,
                    pathAddress,
                    0,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
    }

    /**
     * Asynchronously closes this file.
     */
    suspend fun close() {
        MemoryUtils.freeMemory(pathAddress)
        suspendCancellableCoroutine {
            executor.executeCommand(
                Command.close(fd, executor, CoroutineResultProvider.newInstance(it))
            )
        }
    }
}
