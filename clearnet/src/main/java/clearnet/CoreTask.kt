package clearnet

import clearnet.error.ClearNetworkException
import clearnet.model.PostParams
import io.reactivex.subjects.ReplaySubject
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.Comparator

class CoreTask internal constructor(
        val postParams: PostParams
) {
    val id = idIterator.incrementAndGet()
    val cacheKey: String by lazy { postParams.cacheKey }
    val requestKey: String by lazy { postParams.flatRequest }
    val startTime = System.currentTimeMillis()

    private val inQueues: MutableList<InvocationBlockType> = ArrayList(2)
    private val results = ReplaySubject.create<Result>()
    private val delivered = ReplaySubject.create<Result>().toSerialized()

    fun getLastResult(): SuccessResult = results.values
            .map { it as Result }
            .last { !it.isAncillary && it is SuccessResult } as SuccessResult

    // Warning not reactive transformations
    fun getLastErrorResult(): ErrorResult = results.values
                .map { it as Result }
                .last { !it.isAncillary && it is ErrorResult} as ErrorResult

    fun deliver(result: Result) = delivered.onNext(result)

    fun getRequestIdentifier() = postParams.requestTypeIdentifier

    // todo no queues
    fun move(from: InvocationBlockType?, to: Array<InvocationBlockType>) = synchronized(inQueues) {
        if (from != null) inQueues.remove(from)
        inQueues.addAll(to)
        if (inQueues.isEmpty()) results.onComplete()
    }

    @Deprecated("")
    fun isFinished() = results.hasComplete()

    private fun resolveNextIndexes(index: InvocationBlockType, isSuccess: Boolean) = postParams.invocationStrategy[index][isSuccess]

    fun respond(method: String, params: String?): Boolean {
        return postParams.requestTypeIdentifier == method && (params == null || cacheKey == params)
    }

    internal fun observe() = delivered.hide()

    internal fun promise() = Promise().apply {
        observe().subscribe(results::onNext)    // only elements
    }

    companion object {
        private val idIterator = AtomicLong()
    }


    open class Result(val nextIndexes: Array<InvocationBlockType>, internal val isAncillary: Boolean = true)

    class ErrorResult(val error: ClearNetworkException, nextIndexes: Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)

    class SuccessResult(val result: kotlin.Any?, val plainResult: kotlin.String?, nextIndexes: kotlin.Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)



    inner class Promise {
        private val resultSubject = ReplaySubject.create<Result>()
        val taskRef = this@CoreTask
        internal fun observe() = resultSubject.hide()

        // Unfortunately we must handle null responses
        fun setResult(result: Any?, plainResult: String?, from: InvocationBlockType) {
            dispatch(SuccessResult(result, plainResult, resolveNextIndexes(from, true)))
        }

        fun setError(exception: ClearNetworkException, from: InvocationBlockType) {
            dispatch(ErrorResult(exception, resolveNextIndexes(from, false)))
        }

        fun next(from: InvocationBlockType, success: Boolean = true) = setNextIndexes(resolveNextIndexes(from, success))

        fun pass(from: InvocationBlockType) = next(from, false)

        /**
         * Edit the InvocationStrategy flow:
         * a manual set index will be used instead of InvocationStrategy's indexes
         */
        fun setNextIndex(nextIndex: InvocationBlockType) = setNextIndexes(arrayOf(nextIndex))

        /**
         * Edit the InvocationStrategy flow:
         * manual set indexes will be used instead of InvocationStrategy's indexes
         */
        fun setNextIndexes(nextIndexes: Array<InvocationBlockType>) {
            dispatch(Result(nextIndexes))
        }

        private fun dispatch(result: Result) {
            resultSubject.onNext(result)
            resultSubject.onComplete()
        }
    }

    object ResultsCountComparator : Comparator<CoreTask> {
        override fun compare(p0: CoreTask, p1: CoreTask) = p0.results.values.size.compareTo(p1.results.values.size)
    }
}