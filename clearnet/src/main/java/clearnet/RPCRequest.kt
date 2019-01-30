package clearnet

import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong

class RPCRequest(val method: String) {
    var id: Long = idCounter.incrementAndGet()
    private val jsonrpc = "2.0"
    var params: Any? = null
        private set

    @Transient private var bodyState: Boolean = false


    fun addParameter(name: String, value: Any?): RPCRequest {
        if (bodyState) throw IllegalStateException("The body already has been set")
        if (params == null) params = HashMap<Any, Any?>()

        @Suppress("UNCHECKED_CAST")
        (params as MutableMap<String, Any?>).put(name, value)
        return this
    }

    fun setParamsBody(paramsBody: Any?) {
        if (params != null) throw IllegalStateException("The params already have been set")
        params = paramsBody
        bodyState = true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RPCRequest) return false

        if (method != other.method) return false
        if (params != other.params) return false

        return true
    }

    override fun hashCode() = method.hashCode()

    companion object {
        private val idCounter = AtomicLong()
    }
}
