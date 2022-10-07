package one.kuring.benchmark

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import one.kuring.*
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.math.max

class BenchmarkWorkerIoUring(
    private val path: Path,
    private val bufferSize: Int,
    private val submitBatchSize: Int,
    private val blockSize: Int,
    private val ioDepth: Int,
    private val fixedBuffers: Boolean,
) : BenchmarkWorker(path, bufferSize, blockSize) {


    private val eventExecutor: EventExecutor
    private var buffers: Array<ByteBuffer>? = null

    init {
        val builder = EventExecutor.builder()
        if (fixedBuffers) {
            builder.withBufRing(ioDepth, bufferSize)
        } else {
            buffers = Array(ioDepth) {
                MemoryUtils.allocateAlignedByteBuffer(
                    bufferSize,
                    Native.getPageSize()
                )
            }
        }
        eventExecutor = builder.build()
    }


    override fun run() = runBlocking {
        val file = BufferedFile.open(path, eventExecutor, OpenOption.READ_ONLY, OpenOption.NOATIME)
        val maxBlocks = Native.getFileSize(file.fd) / blockSize
        if (!fixedBuffers) {
            val localBuffers = buffers!!
            if (submitBatchSize == 1) {
                do {
                    calls++
                    val r = file.read(localBuffers[0], getOffset(maxBlocks), bufferSize)
                    reaps++
                    if (r != bufferSize) {
                        println("Unexpected ret: $r")
                    }
                    localBuffers[0].clear()
                    done++
                } while (isRunning)
            } else {
                withContext(this.coroutineContext) {
                    val pending = arrayOfNulls<Deferred<Int>>(submitBatchSize)
                    do {
                        calls++
                        for (i in 0 until submitBatchSize) {
                            pending[i] = async {
                                val r = file.read(localBuffers[i], getOffset(maxBlocks), bufferSize)
                                localBuffers[i].clear()
                                r
                            }
                        }
                        pending.forEach {
                            val r = it?.await()
                            reaps++
                            if (r != bufferSize) {
                                println("Unexpected ret: $r")
                            }
                            done++
                        }
                    } while (isRunning)
                }
            }
        } else {
            if (submitBatchSize == 1) {
                do {
                    calls++
                    file.readFixedBuffer(getOffset(maxBlocks)).use {
                        reaps++
                        if (it.readBytes != bufferSize) {
                            println("Unexpected ret: ${it.readBytes}")
                        }
                        done++
                    }
                } while (isRunning)
            } else {
                withContext(this.coroutineContext) {
                    val pending = arrayOfNulls<Deferred<BufRingResult>>(submitBatchSize)
                    do {
                        calls++
                        for (i in 0 until submitBatchSize) {
                            pending[i] = async { file.readFixedBuffer(getOffset(maxBlocks)) }
                        }
                        pending.forEach { deferred ->
                            deferred?.await()!!.use { bufRingResult ->
                                reaps++
                                if (bufRingResult.readBytes != bufferSize) {
                                    println("Unexpected ret: ${bufRingResult.readBytes}")
                                }
                            }
                            done++
                        }
                    } while (isRunning)
                }
            }
        }
    }
}
