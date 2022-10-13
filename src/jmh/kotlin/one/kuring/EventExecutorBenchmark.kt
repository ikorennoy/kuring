package one.kuring

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openjdk.jmh.annotations.*

@Fork(2)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@State(Scope.Thread)
open class EventExecutorBenchmark {


    lateinit var eventExecutor: EventExecutor

    @Setup
    fun setup() {
        eventExecutor = EventExecutor.initDefault()
    }

    @TearDown
    fun tearDown() {
        eventExecutor.close()
    }

    @Benchmark
    fun executeSingle(): Int {
        return runBlocking {
            suspendCancellableCoroutine {
                eventExecutor.executeCommand(Command.nop(eventExecutor, CoroutineResultProvider.newInstance(it)))
            }
        }
    }

    @Benchmark
    @GroupThreads(3)
    fun executeMulti(): Int {
        return runBlocking {
            suspendCancellableCoroutine {
                eventExecutor.executeCommand(Command.nop(eventExecutor, CoroutineResultProvider.newInstance(it)))
            }
        }
    }
}
