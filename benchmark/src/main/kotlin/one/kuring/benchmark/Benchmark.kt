package one.kuring.benchmark

import picocli.CommandLine
import picocli.CommandLine.Command
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
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

    @CommandLine.Option(names = ["-c", "--complete"], description = ["Batch complete, default 32"], paramLabel = "<int>")
    var complete: Int = 32

    @CommandLine.Option(names = ["-b", "--buffer"], description = ["Buffer size, default 4096"], paramLabel = "<int>")
    var bufferSize: Int = 4096

    @CommandLine.Option(names = ["-w", "--workers"], description = ["Number of threads, default 1"], paramLabel = "<int>")
    var threads: Int = 1



    override fun call(): Int {
        print("file=$file, ")
        print("ioDepth=$ioDepth, ")
        print("submit=$submit, ")
        print("complete=$complete, ")
        print("bufferSize=$bufferSize, ")
        println("threads=$threads")

        var maxIops: Long = -1
        val workers: MutableList<BenchmarkWorker> = ArrayList()
        for (i in 0 until threads) {
            val worker = BenchmarkWorker(Paths.get(file), bufferSize, submit, bufferSize)
            worker.start()
            workers.add(worker)
        }
        var reaps: Long = 0
        var calls: Long = 0
        var done: Long = 0
        do {
            var thisDone: Long = 0
            var thisReap: Long = 0
            var thisCall: Long = 0
            var rpc: Long = 0
            var ipc: Long = 0
            var iops: Long = 0
            var bw: Long = 0

            Thread.sleep(1000)

            for (i in 0 until threads) {
                thisDone += workers[i].done
                thisCall += workers[i].calls
                thisReap += workers[i].reaps
            }

            if ((thisCall - calls) > 0) {
                rpc = (thisDone - done) / (thisCall - calls)
                ipc = (thisReap - reaps) / (thisCall - calls)
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
            reaps = thisReap
        } while (true)

    }
}

fun main(args: Array<String>) : Unit = exitProcess(CommandLine(Benchmark()).execute(*args))
