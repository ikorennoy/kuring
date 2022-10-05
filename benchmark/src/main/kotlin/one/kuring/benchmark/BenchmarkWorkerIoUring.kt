package one.kuring.benchmark

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import one.kuring.*
import java.nio.file.Path

class BenchmarkWorkerIoUring(
    private val path: Path,
    private val bufferSize: Int,
    private val submitBatchSize: Int,
    private val blockSize: Int,
    private val ioDepth: Int,
) : BenchmarkWorker(path, bufferSize, blockSize) {


    private val eventExecutor = EventExecutor
        .builder()
        .entries(ioDepth)
        .build()

    private val buffers = Array(ioDepth) { MemoryUtils.allocateAlignedByteBuffer(bufferSize, Native.getPageSize()) }


    override fun run() = runBlocking {
        val file = BufferedFile.open(path, eventExecutor, OpenOption.READ_ONLY, OpenOption.NOATIME)
        val maxBlocks = Native.getFileSize(file.fd) / blockSize

        if (submitBatchSize == 1) {
            do {
                calls++
                val r = file.read(buffers[0], getOffset(maxBlocks), bufferSize)
                reaps++
                if (r != bufferSize) {
                    println("Unexpected ret: $r")
                }
                buffers[0].clear()
                done++
            } while (isRunning)
        } else {
            withContext(this.coroutineContext) {
                val pending = arrayOfNulls<Deferred<Int>>(submitBatchSize)
                do {
                    calls++
                    for (i in 0 until submitBatchSize) {
                        pending[i] = async {
                            val r = file.read(buffers[i], getOffset(maxBlocks), bufferSize)
                            buffers[i].clear()
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
    }

}