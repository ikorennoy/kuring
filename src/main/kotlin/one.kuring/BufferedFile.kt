package one.kuring

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.Path

class BufferedFile private constructor(
    path: String,
    pathPtr: Long,
    fd: Int,
    pollableStatus: PollableStatus,
    executor: EventExecutor
) : AbstractFile(path, pathPtr, fd, pollableStatus, executor) {

    companion object {
        suspend fun open(
            path: Path,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): BufferedFile {
            return open(path.normalize().toAbsolutePath().toString(), 438, executor, *openOption)
        }

        suspend fun open(
            path: Path,
            executor: EventExecutor,
        ): BufferedFile {
            return open(path.normalize().toAbsolutePath().toString(), 438, executor, OpenOption.READ_ONLY)
        }

        suspend fun open(
            path: Path,
            mode: Int,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): BufferedFile {
            return open(path.normalize().toAbsolutePath().toString(), mode, executor, *openOption)
        }

        suspend fun open(
            path: String,
            executor: EventExecutor,
        ): BufferedFile {
            return open(path, 438, executor, OpenOption.READ_ONLY)
        }

        suspend fun open(
            path: String,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): BufferedFile {
            return open(path, 438, executor, *openOption)
        }

        suspend fun open(
            path: String,
            mode: Int,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): BufferedFile {
            val pathPtr = MemoryUtils.getStringPtr(path)
            val fd = suspendCancellableCoroutine<Int> {
                executor.executeCommand(
                    Command.openAt(
                        OpenOption.toFlags(*openOption),
                        pathPtr,
                        mode,
                        executor,
                        CoroutineResultProvider.newInstance(it)
                    )
                )
            }
            return BufferedFile(path, pathPtr, fd, PollableStatus.NON_POLLABLE, executor)
        }
    }

    /**
     * Requires pipe to be created
     */
    private suspend fun copyTo(srcOffset: Long, dst: BufferedFile, dstOffset: Long, length: Int) {
        suspendCancellableCoroutine<Int> {
            executor.executeCommand(
                Command.splice(
                    fd,
                    srcOffset,
                    dst.fd,
                    dstOffset,
                    length,
                    0,
                    executor,
                    CoroutineResultProvider.newInstance(it)
                )
            )
        }
    }
}