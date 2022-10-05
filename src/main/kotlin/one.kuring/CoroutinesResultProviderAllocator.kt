package one.kuring

import cn.danielw.fop.ObjectFactory

internal class CoroutinesResultProviderAllocator: ObjectFactory<CoroutineResultProvider> {
    override fun create(): CoroutineResultProvider {
        return CoroutineResultProvider()
    }

    override fun validate(t: CoroutineResultProvider?): Boolean {
        return true
    }

    override fun destroy(t: CoroutineResultProvider?) {
    }
}