package one.kuring

import cn.danielw.fop.DisruptorObjectPool
import cn.danielw.fop.ObjectPool
import cn.danielw.fop.PoolConfig
import cn.danielw.fop.Poolable
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class CoroutineResultProvider : ResultProvider<Int> {
    private var continuation: CancellableContinuation<Int>? = null
    private var handle: Poolable<CoroutineResultProvider>? = null

    override fun onSuccess(result: Int) {
        try {
            continuation?.resume(result)
        } finally {
            release()
        }
    }

    override fun onSuccess(`object`: Any?) {

    }

    override fun onError(ex: Throwable) {
        try {
            continuation?.resumeWithException(ex)
        } finally {
            release()
        }
    }

    override fun getInner(): Int? {
        return null
    }

    override fun release() {
        continuation = null
        handle?.close()
    }

    companion object {
        private val allocator = CoroutinesResultProviderAllocator()
        private val poolConfig = PoolConfig()
        private val pool: ObjectPool<CoroutineResultProvider>

        init {
            poolConfig.partitionSize = 100
            poolConfig.maxSize = 50
            pool = DisruptorObjectPool(poolConfig, allocator)
        }

        fun newInstance(continuation: CancellableContinuation<Int>): CoroutineResultProvider {
            val handle = pool.borrowObject()
            val result = handle.`object`
            result.handle = handle
            result.continuation = continuation
            return result
        }
    }
}
