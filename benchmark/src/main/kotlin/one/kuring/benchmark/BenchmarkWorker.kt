package one.kuring.benchmark

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import one.kuring.BufferedFile
import one.kuring.EventExecutor
import java.lang.annotation.Native
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

class BenchmarkWorker(
    val path: Path,
    val bufferSize: Int,
    val submitBatchSize: Int,
    val blockSize: Int,
) : Runnable {

    val thread: Thread = Thread(this)

    @Volatile
    var executed: Int = 0

    @Volatile
    var calls: Long = 0

    @Volatile
    var done: Long = 0

    @Volatile
    var reaps: Long = 0;

    @Volatile
    var isRunning: Boolean = true


    private val eventExecutor = EventExecutor.initDefault()
    private val random = ThreadLocalRandom.current()


    override fun run() = runBlocking {
        val file = BufferedFile.open(path, eventExecutor)
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val size = one.kuring.Native.getFileSize(file.fd)
        val maxBlocks = size / blockSize

        if (submitBatchSize == 1) {
            do {

                val r = file.read(buffer, getOffset(maxBlocks), bufferSize)
                if (r != bufferSize) {
                    println("Unexpected ret: $r")
                }
                buffer.clear()
                calls++
                reaps += 1
                done += 1
            } while (isRunning)
        }
    }

    private fun getOffset(maxBlocks: Long): Long {
        return (abs(random.nextLong()) % (maxBlocks - 1)) * blockSize
    }

    fun start() {
        thread.start()
    }
}