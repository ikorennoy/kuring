package one.kuring

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class CommonFileTests {

    companion object {
        private val DEFAULT_ALIGNMENT = Native.getPageSize().toInt()

        suspend fun preAllocate_notEmptyFile(testFilePair: Pair<Path, AbstractFile>) {
            val (tempFile, abstractFile) = testFilePair
            assertEquals(0, abstractFile.size())
            initTestFile(tempFile, 512)
            assertEquals(0, abstractFile.preAllocate(512, 512))
            assertEquals(1024, abstractFile.size())
            Files.deleteIfExists(tempFile)
        }

        suspend fun size_smallFile(testFilePair: Pair<Path, AbstractFile>) {
            val (tempFile, abstractFile) = testFilePair
            val random = Random()
            for (i in 0..9) {
                val testSize = random.nextInt(1000)
                initTestFile(tempFile, testSize)
                assertEquals(testSize.toLong(), abstractFile.size())
            }
            Files.deleteIfExists(tempFile)
        }

        suspend fun size_largeFile(testFilePair: Pair<Path, AbstractFile>) {
            val (tempFile, abstractFile) = testFilePair
            val testSize = Int.MAX_VALUE * 2L
            initTestFile(tempFile, 10)
            RandomAccessFile(tempFile.toFile(), "rw").use { f ->
                val channel = f.channel
                channel.map(FileChannel.MapMode.READ_WRITE, testSize, 10)
                assertEquals(testSize + 10, abstractFile.size())
            }
            Files.deleteIfExists(tempFile)
        }

        suspend fun close(testFilePair: Pair<Path, AbstractFile>) {
            val (_, abstractFile) = testFilePair
            abstractFile.close()
            assertFailsWith<IOException> { abstractFile.read(ByteBuffer.allocateDirect(10)) }
        }

        suspend fun size_zero(testFile: Pair<Path, AbstractFile>) {
            assertEquals(0, testFile.second.size())
        }

        suspend fun dataSync(testFile: Pair<Path, AbstractFile>) {
            assertEquals(0, testFile.second.dataSync())
        }

        suspend fun preAllocate_emptyFile(testFile: Pair<Path, AbstractFile>) {
            assertEquals(0, testFile.second.size())
            assertEquals(0, testFile.second.preAllocate(1024, 0))
            assertEquals(1024, testFile.second.size())
        }

        suspend fun remove(testFile: Pair<Path, AbstractFile>) {
            assertTrue(Files.exists(testFile.first))
            assertEquals(0, testFile.second.remove())
            assertFalse(Files.exists(testFile.first))
        }

        suspend fun dataSync_closedFile(testFile: Pair<Path, AbstractFile>) {
            testFile.second.close()

            assertFailsWith<IOException> { testFile.second.dataSync() }
        }

        suspend fun remove_removed(testFile: Pair<Path, AbstractFile>) {
            assertTrue(Files.exists(testFile.first))
            assertEquals(0, testFile.second.remove())
            assertFalse(Files.exists(testFile.first))

            assertFailsWith<IOException> { testFile.second.remove() }

        }

        suspend fun remove_readOnly(testFile: Pair<Path, AbstractFile>) {
            assertTrue(Files.exists(testFile.first))
            assertEquals(0, testFile.second.remove())
            assertFalse(Files.exists(testFile.first))
        }

        suspend fun remove_closed(testFile: Pair<Path, AbstractFile>) {
            assertTrue(Files.exists(testFile.first))
            testFile.second.close()
            assertFailsWith<IOException> { testFile.second.remove() }
        }

        suspend fun write(testFile: Pair<Path, AbstractFile>) {
            val expected = prepareString(270)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(
                DEFAULT_ALIGNMENT,
                DEFAULT_ALIGNMENT.toLong()
            )
            byteBuffer.put(expected.substring(0, DEFAULT_ALIGNMENT).toByteArray(StandardCharsets.UTF_8))
            byteBuffer.flip()
            assertEquals(
                DEFAULT_ALIGNMENT,
                testFile.second.write(byteBuffer, 0, DEFAULT_ALIGNMENT)
            )
            assertEquals(DEFAULT_ALIGNMENT.toLong(), Files.size(testFile.first))
            assertEquals(expected.substring(0, DEFAULT_ALIGNMENT), String(Files.readAllBytes(testFile.first)))
        }

        suspend fun write_lengthGreaterThanBufferSize(testFile: Pair<Path, AbstractFile>) {
            val expected = prepareString(50)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(512, DEFAULT_ALIGNMENT.toLong())
            byteBuffer.put(expected.substring(0, 512).toByteArray(StandardCharsets.UTF_8)).flip()
            assertFailsWith<IllegalArgumentException> { testFile.second.write(byteBuffer, 0, 1024) }
        }

        suspend fun read_1(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(100)
            val readLength = 1024
            writeStringToFile(expected, tempFile)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(readLength, DEFAULT_ALIGNMENT.toLong())
            val read: Int = testFile.second.read(byteBuffer, 0, readLength)
            byteBuffer.flip()
            assertEquals(readLength, read)
            assertEquals(read, byteBuffer.limit())
            assertEquals(0, byteBuffer.position())
            val actual = StandardCharsets.UTF_8.decode(byteBuffer).toString()
            assertEquals(expected.substring(0, readLength), actual)
        }

        suspend fun read_lengthGreaterThanBufferSize(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(100)
            val readLength = 2048
            writeStringToFile(expected, tempFile)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(1024, DEFAULT_ALIGNMENT.toLong())
            assertFailsWith<IllegalArgumentException> { testFile.second.read(byteBuffer, 0, readLength) }
        }

        suspend fun read_positionGreaterThanFileSize(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(10)
            val readLength = expected.length
            writeStringToFile(expected, tempFile)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(2048, DEFAULT_ALIGNMENT.toLong())
            assertEquals(0, testFile.second.read(byteBuffer, 2048, readLength))
        }


        suspend fun write_positionGreaterThanFileSize(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(100)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(1024, DEFAULT_ALIGNMENT.toLong())
            byteBuffer.put(expected.substring(0, 1024).toByteArray(StandardCharsets.UTF_8))
            byteBuffer.flip()
            assertEquals(1024, testFile.second.write(byteBuffer, 512, 1024))
            assertEquals(1536, Files.size(tempFile))
            assertEquals(expected.substring(0, 1024), String(Files.readAllBytes(tempFile)).substring(512))
        }


        suspend fun write_lengthLessThenBufferSize(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(100)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(1024, DEFAULT_ALIGNMENT.toLong())
            byteBuffer.put(expected.substring(0, 1024).toByteArray(StandardCharsets.UTF_8))
            byteBuffer.flip()
            assertEquals(1024, testFile.second.write(byteBuffer, 0, 1024))
            assertEquals(1024, Files.size(tempFile))
            assertEquals(expected.substring(0, 1024), String(Files.readAllBytes(tempFile)))
        }

        suspend fun write_trackPosition(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            assertEquals(0, Files.size(tempFile))
            val str = prepareString(100).substring(0, 512)
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(bytes.size, DEFAULT_ALIGNMENT.toLong())
            byteBuffer.put(bytes)
            byteBuffer.flip()
            val written: Int = testFile.second.write(byteBuffer, -1, bytes.size)
            assertEquals(Files.size(tempFile).toInt(), written)
            assertEquals(str, String(Files.readAllBytes(tempFile)))
            byteBuffer.flip()
            testFile.second.write(byteBuffer, -1, bytes.size)
            assertEquals(Files.size(tempFile).toInt(), written * 2)
            assertEquals(str + str, String(Files.readAllBytes(tempFile)))
        }

        suspend fun write_lengthZero(testFile: Pair<Path, AbstractFile>) {
            assertEquals(0, Files.size(testFile.first))
            val str = prepareString(100)
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(bytes.size, DEFAULT_ALIGNMENT.toLong())
            byteBuffer.put(bytes)
            byteBuffer.flip()
            val written: Int = testFile.second.write(byteBuffer, 0, 0)
            assertEquals(0, written)
            assertEquals(0, Files.size(testFile.first))
        }

        suspend fun writev(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            assertEquals(0, Files.size(tempFile))

            val strings = StringBuilder()
            val str = prepareString(100).substring(0, 512)
            val bytes = str.toByteArray(StandardCharsets.UTF_8)
            val buffers = Array(10) {
                MemoryUtils.allocateAlignedByteBuffer(
                    bytes.size,
                    DEFAULT_ALIGNMENT.toLong()
                )
            }

            for (byteBuffer in buffers) {
                byteBuffer.put(bytes)
                byteBuffer.flip()
                strings.append(str)
            }
            val written: Int = testFile.second.write(buffers, 0)
            assertEquals(Files.size(tempFile).toInt(), written)
            assertEquals(strings.toString(), String(Files.readAllBytes(tempFile)))
        }


        suspend fun read_lengthLessThenBufferSize(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val expected = prepareString(100)
            val readLength = 1024
            writeStringToFile(expected, tempFile)
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(2048, DEFAULT_ALIGNMENT.toLong())
            assertEquals(readLength, testFile.second.read(byteBuffer, 0, readLength))
            byteBuffer.flip()
            val actual = StandardCharsets.UTF_8.decode(byteBuffer).toString()
            assertEquals(expected.substring(0, readLength), actual)
        }

        suspend fun read_bufferGreaterThanFile(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val resultString = prepareString(100).substring(0, 512)
            writeStringToFile(resultString, tempFile)
            val stringLength = resultString.toByteArray(StandardCharsets.UTF_8).size
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(
                stringLength * 2,
                DEFAULT_ALIGNMENT.toLong()
            )
            val bytes: Int = testFile.second.read(byteBuffer, 0, byteBuffer.capacity())
            byteBuffer.flip()
            assertEquals(stringLength, bytes)
            assertEquals(resultString, StandardCharsets.UTF_8.decode(byteBuffer).toString())
        }

        suspend fun read_bufferLessThanFile(testFile: Pair<Path, AbstractFile>) {
            val tempFile = testFile.first
            val resultString = prepareString(300).substring(0, 1024)
            writeStringToFile(resultString, tempFile)
            val stringLength = resultString.toByteArray(StandardCharsets.UTF_8).size
            val byteBuffer = MemoryUtils.allocateAlignedByteBuffer(
                stringLength / 2,
                DEFAULT_ALIGNMENT.toLong()
            )
            val bytes: Int = testFile.second.read(byteBuffer, 0, byteBuffer.capacity())
            byteBuffer.flip()
            assertEquals(stringLength / 2, bytes)
            assertEquals(resultString.substring(0, 512), StandardCharsets.UTF_8.decode(byteBuffer).toString())
        }

        fun open_newFile(testFile: Pair<Path, AbstractFile>) {
            assertTrue(testFile.second.fd > 0)
            assertTrue(Files.exists(testFile.first))
        }

        suspend fun bufRing_oneBuffer(testFile: Pair<Path, AbstractFile>) {
            val expected = prepareString(1000)
            writeStringToFile(expected, testFile.first)
            var bufRingResult: BufRingResult = testFile.second.readFixedBuffer(-1)

            val builder = StringBuilder()
            while (bufRingResult.readBytes != 0) {
                bufRingResult.buffer.flip()
                builder.append(StandardCharsets.UTF_8.decode(bufRingResult.buffer))
                bufRingResult.close()
                bufRingResult = testFile.second.readFixedBuffer(-1)
            }
            assertEquals(expected, builder.toString())
        }

        suspend fun bufRing(testFile: Pair<Path, AbstractFile>) {
            val expected = prepareString(1000)
            writeStringToFile(expected, testFile.first)
            var bufRingResult: BufRingResult = testFile.second.readFixedBuffer(-1)
            val builder = StringBuilder()
            while (bufRingResult.readBytes != 0) {
                bufRingResult.buffer.flip()
                builder.append(StandardCharsets.UTF_8.decode(bufRingResult.buffer))
                bufRingResult.close()
                bufRingResult = testFile.second.readFixedBuffer(-1)
            }
            assertEquals(expected, builder.toString())
        }

        suspend fun bufRing_notRegistered(testFile: Pair<Path, AbstractFile>) {
            assertFailsWith<IllegalStateException> { testFile.second.readFixedBuffer(-1) }
        }

        suspend fun bufRing_registeredNoBuffers(testFile: Pair<Path, AbstractFile>) {
            val bufRingResult: BufRingResult = testFile.second.readFixedBuffer(-1)
            assertFailsWith<IOException> { testFile.second.readFixedBuffer(-1) }
            bufRingResult.close()
        }


        private fun prepareString(iters: Int): String {
            val sb = StringBuilder()
            val s = "String number "
            for (i in 0 until iters) {
                sb.append(s).append(i).append("\n")
            }
            return sb.toString()
        }

        private fun writeStringToFile(stringToWrite: String, f: Path) {
            Files.write(f, stringToWrite.toByteArray(StandardCharsets.UTF_8))
        }


        private fun initTestFile(tempFile: Path, testSize: Int) {
            val bytes = ByteArray(testSize)
            for (i in 0 until testSize) {
                bytes[i] = 'e'.code.toByte()
            }
            Files.write(
                tempFile,
                bytes,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
            )
        }
    }


}