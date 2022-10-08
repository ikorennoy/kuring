package one.kuring

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.Path

class AsyncFile private constructor(
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
        ): AsyncFile {
            return open(path.normalize().toAbsolutePath().toString(), 438, executor, *openOption)
        }

        suspend fun open(
            path: Path,
            executor: EventExecutor,
        ): AsyncFile {
            return open(path.normalize().toAbsolutePath().toString(), 438, executor, OpenOption.READ_ONLY)
        }

        suspend fun open(
            path: Path,
            mode: Int,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): AsyncFile {
            return open(path.normalize().toAbsolutePath().toString(), mode, executor, *openOption)
        }

        suspend fun open(
            path: String,
            executor: EventExecutor,
        ): AsyncFile {
            return open(path, 438, executor, OpenOption.READ_ONLY)
        }

        suspend fun open(
            path: String,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): AsyncFile {
            return open(path, 438, executor, *openOption)
        }

        suspend fun open(
            path: String,
            mode: Int,
            executor: EventExecutor,
            vararg openOption: OpenOption
        ): AsyncFile {
            val pathPtr = MemoryUtils.getStringPtr(path)
            val fd = suspendCancellableCoroutine {
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
            val pollableStatus = if (openOption.contains(OpenOption.DIRECT)) {
                PollableStatus.POLLABLE
            } else {
                PollableStatus.NON_POLLABLE
            }
            return AsyncFile(path, pathPtr, fd, pollableStatus, executor)
        }
    }

    /**
     * Requires pipe to be created
     */
    private suspend fun copyTo(srcOffset: Long, dst: AsyncFile, dstOffset: Long, length: Int) {
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