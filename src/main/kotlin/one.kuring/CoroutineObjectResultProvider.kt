package one.kuring

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineObjectResultProvider(private val continuation: CancellableContinuation<BufRingResult>) :
    ResultProvider<BufRingResult> {


    companion object {
        fun newInstance(cancellableContinuation: CancellableContinuation<BufRingResult>): CoroutineObjectResultProvider {
            return CoroutineObjectResultProvider(cancellableContinuation)
        }
    }


    override fun onSuccess(result: Int) {

    }

    override fun onSuccess(any: Any?) {
        continuation.resume(any as BufRingResult)
    }

    override fun onError(ex: Throwable) {
        continuation.resumeWithException(ex)
    }

    override fun getInner(): BufRingResult? {
        return null
    }

    override fun release() {
    }
}