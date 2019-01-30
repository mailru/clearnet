package clearnet.interfaces

import clearnet.CoreTask
import clearnet.InvocationBlockType
import clearnet.error.ClearNetworkException
import clearnet.error.ConversionException
import clearnet.error.HTTPCodeError
import clearnet.model.MergedInvocationStrategy
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import io.reactivex.schedulers.Schedulers
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.reflect.Type
import java.util.*

interface ConversionStrategy {
    @Throws(JSONException::class, ConversionStrategyError::class)
    fun checkErrorOrResult(response: JSONObject): String?

    fun init(parameter: String){
        // nop
    }

    class ConversionStrategyError(val serializedError: String?, val errorType: Type) : Exception()

    object SmartConverter {
        @Throws(ClearNetworkException::class)
        fun convert(converter: ISerializer, body: String, type: Type, strategy: ConversionStrategy): Any? {
            return converter.deserialize(getStringResultOrThrow(converter, body, strategy), type)
        }

        @Throws(ClearNetworkException::class)
        fun getStringResultOrThrow(converter: ISerializer, body: String, strategy: ConversionStrategy): String? {
            try {
                return getStringResultOrThrow(converter, JSONObject(body), strategy)
            } catch (e: JSONException) {
                throw ConversionException(e)
            }
        }

        @Throws(ClearNetworkException::class)
        fun getStringResultOrThrow(converter: ISerializer, body: JSONObject, strategy: ConversionStrategy): String? {
            try {
                return strategy.checkErrorOrResult(body)
            } catch (e: JSONException) {
                throw ConversionException(e)
            } catch (e: ConversionStrategyError) {
                throw clearnet.error.ResponseErrorException(converter.deserialize(e.serializedError, e.errorType))
            }
        }
    }
}

/**
 * Validates fields of model instances which has been deserialized from the server response.
 * The validator must check required fields and can contains some additional checking rules.
 */
interface IBodyValidator {
    @Throws(clearnet.error.ValidationException::class)
    fun validate(body: Any?)
}

interface ICacheProvider {
    fun store(key: String, value: String, expiresAfter: Long)

    fun obtain(key: String): String?
}

/**
 * The upper level abstraction of [IRequestExecutor].
 * It should serialize the object, send the request and deserialize the response to the model.
 */
interface IConverterExecutor {
    fun executePost(postParams: PostParams)
}

/**
 * Request executor which should just push data to server and return response as String
 */
interface IRequestExecutor {
    @Throws(IOException::class, HTTPCodeError::class)
    fun executeGet(headers: Map<String, String>, queryParams: Map<String, String> = emptyMap()): Pair<String, Map<String, String>>

    @Throws(IOException::class, HTTPCodeError::class)
    fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String, String> = emptyMap()): Pair<String, Map<String, String>>
}

/**
 * Serializes and deserializes models to/from String
 */
interface ISerializer {
    @Throws(ConversionException::class)
    fun serialize(obj: Any?): String

    @Throws(ConversionException::class)
    fun deserialize(body: String?, objectType: Type): Any?
}

interface ISmartConverter {
    @Throws(ClearNetworkException::class)
    fun convert(body: String, type: Type, strategy: ConversionStrategy): Any?
}

/**
 * Request executor non typed callback for inner use
 */
@Deprecated("Use reactive streams")
interface RequestCallback<T> {

    /**
     * @param response – the deserialized model
     */
    fun onSuccess(response: T)

    /**
     * @param exception – the exception, which can be caught during request, conversation or validation processes
     */
    fun onFailure(exception: ClearNetworkException)
}

interface ICallbackHolder {
    val scheduler: Scheduler
        get() = ImmediateThinScheduler.INSTANCE

    fun init()
    fun hold(disposable: Disposable)
    fun clear()

    @Deprecated("")
    fun <I> createEmpty(type: Class<in I>): I

    @Deprecated("")
    fun <I> wrap(source: I, interfaceType: Class<in I>): I
}

interface HeaderProvider {
    fun obtainHeadersList(): Map<String, String>
}

interface HeaderListener {
    fun onNewHeader(method: String, name: String, value: String)
}

interface HeaderObserver {
    fun register(method: String, listener: HeaderListener, vararg header: String): Subscription
}

interface ICallbackStorage {
    fun <T> observe(method: String): Observable<T>

    @Deprecated("")
    fun subscribe(method: String, callback: RequestCallback<*>, once: Boolean = false): Subscription
}

@Deprecated("")
interface Subscription {
    fun unsubscribe()
}

interface IInvocationBlock {
    val invocationBlockType: InvocationBlockType
    val queueAlgorithm: QueueAlgorithm
        get() = QueueAlgorithm.IMMEDIATE
    val queueTimeThreshold: Long
        get() = 100L

    fun onEntity(promise: CoreTask.Promise) {
        promise.next(invocationBlockType)
    }
    fun onQueueConsumed(promises: List<CoreTask.Promise>) {}

    enum class QueueAlgorithm {
        IMMEDIATE, TIME_THRESHOLD
    }
}

interface TaskTimeTracker {
    fun onTaskFinished(invocationStrategy: MergedInvocationStrategy, method: String, time: Long)
}

interface IInvocationStrategy {
    val algorithm: Map<InvocationBlockType, Decision>
    val metaData: Map<String, String>

    class Decision(private val onResult: Array<InvocationBlockType>, private val onError: Array<InvocationBlockType> = emptyArray()) {
        constructor(onResult: InvocationBlockType) : this(arrayOf(onResult))
        constructor(onResult: InvocationBlockType, onError: InvocationBlockType) : this(arrayOf(onResult), arrayOf(onError))
        constructor(onResult: Array<InvocationBlockType>, onError: InvocationBlockType) : this(onResult, arrayOf(onError))

        operator fun get(hasResult: Boolean) = if (hasResult) onResult else onError
    }
}