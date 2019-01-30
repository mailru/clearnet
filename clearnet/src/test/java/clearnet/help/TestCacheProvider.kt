package clearnet.help

import clearnet.interfaces.ICacheProvider

class TestCacheProvider : ICacheProvider {
    var state: Int = 0
    var returnObject = false

    override fun store(key: String, value: String, expiresAfter: Long) {
        state += 10
    }

    override fun obtain(key: String): String? {
        state++
        return if (returnObject) "{\"result\":\"test\"}" else null
    }
}