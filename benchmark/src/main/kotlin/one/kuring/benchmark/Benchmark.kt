package one.kuring.benchmark

import one.kuring.EventExecutor
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable
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
        println(EventExecutor.initDefault())
        return 0
    }



}

fun main(args: Array<String>) : Unit = exitProcess(CommandLine(Benchmark()).execute(*args))