package one.kuring

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.FileWriter
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferedFileTest {
    private val executor = EventExecutor.initDefault()

    @TempDir
    var tmpDir: Path? = null

    @Test
    fun atomicAppend(): Unit = runBlocking {
        val random = Random()
        val tempFile = Files.createTempFile(tmpDir, "temp-", " file")
        val nThreads = 16
        val writes = 1000
        val pool = Executors.newFixedThreadPool(nThreads)
        for (i in 0 until nThreads) {
            pool.execute {
                runBlocking {
                    for (j in 0 until writes) {
                        try {
                            val bufferedFile: BufferedFile =
                                BufferedFile.open(tempFile, executor, OpenOption.WRITE_ONLY, OpenOption.APPEND)

                            val buffer = ByteBuffer.allocateDirect(1)
                            buffer.put('c'.code.toByte())
                            buffer.flip()
                            if (random.nextBoolean()) {
                                val buffers = arrayOf(buffer)
                                bufferedFile.write(buffers)
                            } else {
                                bufferedFile.write(buffer)
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)
        val bufferedFile: BufferedFile = BufferedFile.open(tempFile, executor, OpenOption.WRITE_ONLY, OpenOption.APPEND)

        assertEquals((nThreads * writes).toLong(), bufferedFile.size())
        Files.deleteIfExists(tempFile)
    }

    @Test
    fun read() = runBlocking {
        val builder = StringBuilder()
        builder.setLength(4)
        val testFile: Pair<Path, AbstractFile> = prepareFile()
        BufferedWriter(FileWriter(testFile.first.toFile())).use { bufferedWriter ->
            for (i in 0..3999) {
                val num = i.toString()
                for (j in 0 until 4 - num.length) {
                    bufferedWriter.write("0")
                }
                bufferedWriter.write(num)
                bufferedWriter.newLine()
            }
        }
        val charPerLine = 5
        for (i in 0..999) {
            val offset = i * charPerLine
            val expectedResult = offset / charPerLine
            val buffer = ByteBuffer.allocateDirect(charPerLine)
            testFile.second.read(buffer)
            for (j in 0..3) {
                val b = buffer[j]
                builder.setCharAt(j, Char(b.toUShort()))
            }
            val result = builder.toString().toInt()
            Assertions.assertEquals(expectedResult, result)
        }
    }

    @Test
    fun scatteringRead_1() = runBlocking {
        val numBuffers = 3
        val bufferCap = 3
        val buffers = Array(numBuffers) { ByteBuffer.allocateDirect(bufferCap) }

        val pair: Pair<Path, AbstractFile> = prepareFile()
        FileOutputStream(pair.first.toFile()).use { fileOutputStream ->
            for (i in -128..127) {
                fileOutputStream.write(i)
            }
        }
        var expectedResult = (-128).toByte()
        for (k in 0..19) {
            pair.second.read(buffers)
            for (i in 0 until numBuffers) {
                for (j in 0 until bufferCap) {
                    val b = buffers[i]!![j]
                    Assertions.assertEquals(b, expectedResult++)
                }
                buffers[i]!!.flip()
            }
        }
        pair.second.close()
    }

    @Test
    fun scatteringRead_2() = runBlocking {
        val byteBuffers = Arrays.asList(
            ByteBuffer.allocateDirect(10),
            ByteBuffer.allocateDirect(10)
        )
            .toTypedArray()
        val pathBufferedFilePair: Pair<Path, AbstractFile> = prepareFile()
        FileOutputStream(pathBufferedFilePair.first.toFile()).use { fileOutputStream ->
            for (i in 0..14) {
                fileOutputStream.write(92)
            }
        }
        pathBufferedFilePair.second.read(byteBuffers)
        Assertions.assertEquals(10, byteBuffers[1].limit())
        pathBufferedFilePair.second.close()
    }

    @Test
    fun buffersUpdate() = runBlocking {
        val bufsNum = 4
        val buffers = Array(bufsNum) { ByteBuffer.allocateDirect(10) }

        buffers[0]!!.put(1.toByte())
        buffers[0]!!.flip()
        buffers[1]!!.put(2.toByte())
        buffers[1]!!.flip()
        buffers[2]!!.put(3.toByte())
        buffers[2]!!.flip()
        buffers[3]!!.put(4.toByte())
        buffers[3]!!.flip()
        val pair: Pair<Path, AbstractFile> = prepareFile(OpenOption.READ_WRITE)
        pair.second.write(buffers, 0, 2)
        val bb = ByteBuffer.allocateDirect(10)
        pair.second.read(bb)
        bb.flip()
        Assertions.assertEquals(1.toByte(), bb.get())
        Assertions.assertEquals(2.toByte(), bb.get())
        Assertions.assertThrows(
            BufferUnderflowException::class.java
        ) { bb.get() }
        pair.second.close()
    }

    @Test
    fun preAllocate_notEmptyFile(): Unit = runBlocking {
        CommonFileTests.preAllocate_notEmptyFile(prepareFile(OpenOption.WRITE_ONLY))
        return@runBlocking
    }

    @Test
    fun size_smallFile() = runBlocking {
        CommonFileTests.size_smallFile(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun size_largeFile() = runBlocking {
        CommonFileTests.size_largeFile(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun close() = runBlocking {
        CommonFileTests.close(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun size_zero() = runBlocking {
        CommonFileTests.size_zero(prepareFile())
    }

    @Test
    fun dataSync() = runBlocking {
        CommonFileTests.dataSync(prepareFile())
    }


    @Test
    fun remove() = runBlocking {
        CommonFileTests.remove(prepareFile())
    }

    @Test
    fun dataSync_closedFile() = runBlocking {
        CommonFileTests.dataSync_closedFile(prepareFile())
    }

    @Test
    fun remove_removed() = runBlocking {
        CommonFileTests.remove_removed(prepareFile())
    }

    @Test
    fun remove_readOnly() = runBlocking {
        CommonFileTests.remove_readOnly(prepareFile())
    }

    @Test
    fun remove_closed() = runBlocking {
        CommonFileTests.remove_closed(prepareFile())
    }

    @Test
    fun write() = runBlocking {
        CommonFileTests.write(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun write_lengthGreaterThanBufferSize() = runBlocking {
        CommonFileTests.write_lengthGreaterThanBufferSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_1() = runBlocking {
        CommonFileTests.read_1(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_lengthGreaterThanBufferSize() = runBlocking {
        CommonFileTests.read_lengthGreaterThanBufferSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_positionGreaterThanFileSize() = runBlocking {
        CommonFileTests.read_positionGreaterThanFileSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun write_positionGreaterThanFileSize() = runBlocking {
        CommonFileTests.write_positionGreaterThanFileSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun write_lengthLessThenBufferSize() = runBlocking {
        CommonFileTests.write_lengthLessThenBufferSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun write_trackPosition() = runBlocking {
        CommonFileTests.write_trackPosition(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun write_lengthZero() = runBlocking {
        CommonFileTests.write_lengthZero(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun writev() = runBlocking {
        CommonFileTests.write_lengthZero(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_lengthLessThenBufferSize() = runBlocking {
        CommonFileTests.read_lengthLessThenBufferSize(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_bufferGreaterThanFile() = runBlocking {
        CommonFileTests.read_bufferGreaterThanFile(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun read_bufferLessThanFile() = runBlocking {
        CommonFileTests.read_bufferLessThanFile(prepareFile(OpenOption.READ_WRITE))
    }

    @Test
    fun open_newFile() = runBlocking {
        CommonFileTests.open_newFile(prepareFile())
    }

    @Test
    @Disabled("required 5.19+ CI kernel version")
    fun bufRing() = runBlocking {
        val ee = EventExecutor.builder()
            .withBufRing(4, 4096).build()
        val tempFile = Files.createTempFile(tmpDir, "test-", " file")
        val file: BufferedFile = BufferedFile.open(tempFile, ee, OpenOption.READ_WRITE)
        CommonFileTests.bufRing(Pair(tempFile, file))
    }

    private fun prepareFile(vararg openOptions: OpenOption = arrayOf(OpenOption.READ_ONLY)): Pair<Path, AbstractFile> =
        runBlocking {
            val tempFile: Path = Files.createTempFile(tmpDir, "test-", " file")
            val file: AbstractFile = BufferedFile.open(tempFile, executor, *openOptions)
            tempFile to file
        }
}