package one.kuring.benchmark

import picocli.CommandLine
import picocli.CommandLine.Command
import java.nio.file.Paths
import java.util.concurrent.Callable
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.system.exitProcess

@Command(
    mixinStandardHelpOptions = true, version = ["Benchmark 1.0"],
)
class Benchmark : Callable<Int> {

    @CommandLine.Parameters(index = "0", description = ["Path to the file"])
    lateinit var file: String

    @CommandLine.Option(names = ["-d", "--depth"], description = ["IO Depth, default 128"], paramLabel = "<int>")
    var ioDepth: Int = 128

    @CommandLine.Option(names = ["-s", "--submit"], description = ["Batch submit, default 32"], paramLabel = "<int>")
    var submit: Int = 32

    @CommandLine.Option(
        names = ["-c", "--complete"],
        description = ["Batch complete, default 32"],
        paramLabel = "<int>"
    )
    var complete: Int = 32

    @CommandLine.Option(names = ["-b", "--buffer"], description = ["Buffer size, default 4096"], paramLabel = "<int>")
    var bufferSize: Int = 4096

    @CommandLine.Option(
        names = ["-w", "--workers"],
        description = ["Number of threads, default 1"],
        paramLabel = "<int>"
    )
    var threads: Int = 1

    @CommandLine.Option(
        names = ["-S", "--sync-io"],
        description = ["Use sync IO, default false"],
        paramLabel = "<bool>"
    )
    var sync: Boolean = false

    @CommandLine.Option(
        names = ["-f", "--fixed-buffers"],
        description = ["Use fixed buffers, default true"],
        paramLabel = "<bool>"
    )
    var useFixedBuffer: Boolean = true

    @CommandLine.Option(
        names = ["-O", "--direct-io"],
        description = ["Use O_DIRECT, default true"],
        paramLabel = "<bool>"
    )
    var useDirectIo: Boolean = true

    @CommandLine.Option(
        names = ["-wp", "--wakeup-percentile"],
        description = ["Publish event loop wakeup delay percentile"],
        paramLabel = "<bool>"
    )
    var eventLoopWakeupPercentile: Boolean = true

    @CommandLine.Option(
        names = ["-F", "--tsc-rate"],
        description = ["TSC rate in HZ"],
        paramLabel = "<int>"
    )
    var tscRate: Long = -1

    private val defaultPercentiles =
        doubleArrayOf(0.01, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99, 0.995, 0.999, 0.9995, 0.9999)


    override fun call(): Int {
        print("File=$file, ")
        print("Io Depth=$ioDepth, ")
        print("Submit=$submit, ")
        print("Complete=$complete, ")
        print("Buffer Size=$bufferSize, ")
        print("Sync I/O=$sync, ")
        print("Fixed buffers=$useFixedBuffer, ")
        print("Direct I/O=$useDirectIo, ")
        println("Threads=$threads")


        var maxIops: Long = -1
        val workers: MutableList<BenchmarkWorker> = ArrayList()
        for (i in 0 until threads) {
            val worker = if (sync) {
                BenchmarkWorkerFileChannel(
                    path = Paths.get(file),
                    bufferSize = bufferSize,
                    blockSize = bufferSize
                )
            } else {
                BenchmarkWorkerIoUring(
                    path = Paths.get(file),
                    bufferSize = bufferSize,
                    submitBatchSize = submit,
                    blockSize = bufferSize,
                    ioDepth = ioDepth,
                    fixedBuffers = useFixedBuffer,
                    directIo = useDirectIo
                )
            }

            worker.start()
            workers.add(worker)
        }

        if (eventLoopWakeupPercentile) {
            Runtime.getRuntime().addShutdownHook(Thread {
                for (i in 0 until workers.size) {
                    val percentiles = workers[i].getPercentiles(defaultPercentiles)
                    val message = if (tscRate != -1L) {
                        "Worker #$i loop wakeup percentiles usec"
                    } else {
                        "Worker #$i loop wakeup percentiles TSC"
                    }
                    println(message)
                    for (j in percentiles.indices) {
                        val value = if (tscRate != -1L) {
                            convertToNsec(percentiles[j]) / 1000.0
                        } else {
                            percentiles[j].toLong()
                        }
                        print("${defaultPercentiles[j] * 100.0}=[${value}], ")
                        if (j.mod(2) == 0) {
                            println()
                        }
                    }
                }
            })
        }

        var reap: Long = 0
        var calls: Long = 0
        var done: Long = 0
        do {
            var thisDone: Long = 0
            var thisReap: Long = 0
            var thisCall: Long = 0
            var rpc: Long
            var ipc: Long
            var iops: Long
            var bw: Long

            Thread.sleep(1000)


            for (i in 0 until threads) {
                thisDone += workers[i].done
                thisCall += workers[i].calls
                thisReap += workers[i].reaps
            }

            if ((thisCall - calls) > 0) {
                rpc = (thisDone - done) / (thisCall - calls)
                ipc = (thisReap - reap) / (thisCall - calls)
            } else {
                rpc = -1
                ipc = -1
            }
            iops = thisDone - done
            bw = if (bufferSize > 1048576) {
                iops * (bufferSize / 1048576)
            } else {
                iops / (1048576 / bufferSize)
            }
            print("IOPS=${iops}, ")
            maxIops = max(maxIops, iops)
            print("BW=${bw}MiB/s, ")
            println("IOS/call=${rpc}/${ipc}")
            done = thisDone
            calls = thisCall
            reap = thisReap
        } while (true)

    }

    private fun convertToNsec(cycles: Double): Long {
        return (cycles.toLong() * 1_000_000_000L) / tscRate
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(Benchmark()).execute(*args))
