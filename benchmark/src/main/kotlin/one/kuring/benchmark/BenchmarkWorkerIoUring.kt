package one.kuring.benchmark

import kotlinx.coroutines.runBlocking
import one.kuring.BufferedFile
import one.kuring.EventExecutor
import one.kuring.OpenOption
import java.nio.ByteBuffer
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


    override fun run() = runBlocking {
        val file = BufferedFile.open(path, eventExecutor, OpenOption.READ_ONLY, OpenOption.NOATIME)
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val maxBlocks = one.kuring.Native.getFileSize(file.fd) / blockSize

        if (submitBatchSize == 1) {
            do {
                calls++
                val r = file.read(buffer, getOffset(maxBlocks), bufferSize)
                reaps++
                if (r != bufferSize) {
                    println("Unexpected ret: $r")
                }
                buffer.clear()
                done++
            } while (isRunning)
        }
    }

}