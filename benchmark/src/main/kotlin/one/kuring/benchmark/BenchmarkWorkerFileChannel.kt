package one.kuring.benchmark

import one.kuring.Native
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path

class BenchmarkWorkerFileChannel(
    private val path: Path,
    private val bufferSize: Int,
    private val blockSize: Int,
) : BenchmarkWorker(path, bufferSize, blockSize) {
    override fun getLoopWakeupPercentiles(defaultPercentiles: DoubleArray): DoubleArray {
        TODO("Not yet implemented")
    }

    override fun getSleepableRingPercentiles(defaultPercentiles: DoubleArray): DoubleArray {
        TODO("Not yet implemented")
    }

    override fun getPollRingLatencies(defaultPercentiles: DoubleArray): DoubleArray {
        TODO("Not yet implemented")
    }

    override fun run() {
        val file = FileChannel.open(path)
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val maxBlocks = getSize() / blockSize
        do {
            calls++
            val r = file.read(buffer, getOffset(maxBlocks))
            if (r != bufferSize) {
                println("Unexpected ret: $r")
            }
            buffer.clear()
            done++

        } while (true)

    }

    private fun getSize(): Long {
        return RandomAccessFile(path.toFile(), "r").use {
            val f = FileDescriptor::class.java.getDeclaredField("fd");
            f.isAccessible = true
            Native.getFileSize(f.get(it.fd) as Int)
        }
    }
}
