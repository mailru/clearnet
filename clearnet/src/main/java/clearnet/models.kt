package clearnet.model

import clearnet.InvocationBlockType
import clearnet.InvocationStrategy
import clearnet.RPCRequest
import clearnet.interfaces.ConversionStrategy
import clearnet.interfaces.IInvocationStrategy
import clearnet.interfaces.IRequestExecutor
import clearnet.interfaces.ISerializer
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject
import java.lang.reflect.Type


abstract class PostParams(
        val httpRequestType: String,
        val requestParams: Map<String, String>,
        open val requestBody: Any?,
        val resultType: Type,
        val requestExecutor: IRequestExecutor,
        val invocationStrategy: MergedInvocationStrategy,
        val expiresAfter: Long,
        val conversionStrategy: ConversionStrategy,
        var headers: Map<String, String>,
        val bindable: Boolean = true,
        val maxBatchSize: Int = 1,
        val subject: Subject<Any> = ReplaySubject.create<Any>().toSerialized()
) {
    abstract val requestTypeIdentifier: String
    abstract val flatRequest: String
    abstract val cacheKey: String
}

class RpcPostParams(
        requestParams: Map<String, String>,
        override val requestBody: RPCRequest,
        resultType: Type,
        requestExecutor: IRequestExecutor,
        invocationStrategy: MergedInvocationStrategy,
        expiresAfter: Long,
        conversionStrategy: ConversionStrategy,
        headers: Map<String, String>,
        bindable: Boolean = true,
        maxBatchSize: Int,
        serializer: ISerializer)
    : PostParams(
        "POST",
        requestParams,
        requestBody,
        resultType,
        requestExecutor,
        invocationStrategy,
        expiresAfter,
        conversionStrategy,
        headers,
        bindable,
        maxBatchSize
) {

    override val requestTypeIdentifier = requestBody.method
    override val flatRequest: String
    override val cacheKey: String

    init {
        flatRequest = serializer.serialize(requestBody)

        val id = requestBody.id
        requestBody.id = 0  // we have to make this hook for making cache not store actual request id as key
        val ccacheKey = serializer.serialize(requestBody)
        requestBody.id = id
        cacheKey = ccacheKey
    }
}


class MergedInvocationStrategy(strategies: Array<InvocationStrategy>) {
    private val algorithm: Map<InvocationBlockType, IInvocationStrategy.Decision>
    private val metaData: MutableMap<String, String>


    operator fun get(index: InvocationBlockType): IInvocationStrategy.Decision = algorithm[index]
            ?: IInvocationStrategy.Decision(emptyArray(), emptyArray())

    @Synchronized
    operator fun get(key: String) = metaData[key]

    @Synchronized
    operator fun set(key: String, value: String?) {
        if (value == null) metaData.remove(key)
        else metaData[key] = value
    }

    init {
        val mergedAlgorithm = mutableMapOf<InvocationBlockType, IInvocationStrategy.Decision>()
        val mergedMeta = mutableMapOf<String, String>()
        strategies.forEach {
            mergedAlgorithm.putAll(it.algorithm)
            mergedMeta.putAll(it.metaData)
        }
        algorithm = mergedAlgorithm
        metaData = mergedMeta
    }
}

data class RpcErrorResponse(val code: Long?, val message: String?, val data: Any?) {
    override fun toString() = "Code: $code, $message. \n $data"
}

data class RpcInnerError(var message: String?, var error: String?) {
    override fun toString() = "$error\n$message"
}

data class RpcInnerErrorResponse(val error: String?, val error_description: String?) {
    override fun toString() = "$error\n$error_description"
}