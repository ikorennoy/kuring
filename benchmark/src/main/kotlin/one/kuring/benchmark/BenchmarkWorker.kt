package one.kuring.benchmark

import java.nio.file.Path
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

abstract class BenchmarkWorker(
    private val path: Path,
    private val bufferSize: Int,
    private val blockSize: Int,
) : Runnable {

    private val thread: Thread = Thread(this)

    private val random = Random(thread.hashCode().toLong())

//    @Volatile
    var calls: Long = 0

//    @Volatile
    var done: Long = 0

//    @Volatile
    var reaps: Long = 0;

//    @Volatile
    var isRunning: Boolean = true


    internal fun getOffset(maxBlocks: Long): Long {
        return (abs(random.nextLong()) % (maxBlocks - 1)) * blockSize
    }

    fun start() {
        thread.start()
    }

    abstract fun getPercentiles(defaultPercentiles: DoubleArray): DoubleArray
}