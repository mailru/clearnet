package clearnet.blocks

import clearnet.*
import clearnet.error.ClearNetworkException
import clearnet.error.ConversionException
import clearnet.error.NetworkException
import clearnet.interfaces.IBodyValidator
import clearnet.interfaces.IInvocationBlock
import clearnet.interfaces.ISerializer
import clearnet.interfaces.ConversionStrategy.SmartConverter
import clearnet.interfaces.HeaderObserver
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.NoSuchElementException

class GetFromNetBlock(
        private val validator: IBodyValidator,
        private val converter: ISerializer
) : IInvocationBlock {
    override val invocationBlockType = InvocationBlockType.GET_FROM_NET
    override val queueAlgorithm = IInvocationBlock.QueueAlgorithm.TIME_THRESHOLD
    private val headersObserver = SimpleHeadersObserver()

    fun getHeadersObserver(): HeaderObserver = headersObserver

    override fun onQueueConsumed(promises: List<CoreTask.Promise>) {
        when {
            promises.isEmpty() -> return
            promises.size == 1 -> obtainFromNet(promises[0])
            checkExecutors(promises) -> groupByBatchSize(promises)
            else -> {
                val runningTasks = ArrayList<CoreTask.Promise>()
                promises.forEach {
                    if (it.taskRef.postParams.requestExecutor === promises[0].taskRef.postParams.requestExecutor) runningTasks.add(it)
                    else it.setNextIndex(InvocationBlockType.GET_FROM_NET)
                }

                if (runningTasks.size == 1) {
                    obtainFromNet(runningTasks[0])
                } else {
                    groupByBatchSize(runningTasks)
                }
            }
        }
    }

    private fun checkExecutors(promises: List<CoreTask.Promise>): Boolean {
        return (1 until promises.size).none { promises[it - 1].taskRef.postParams.requestExecutor !== promises[it].taskRef.postParams.requestExecutor }
    }

    private fun obtainFromNet(promise: CoreTask.Promise) = with(promise.taskRef.postParams) {
        try {
            val responseString: String
            try {
                val result = if (httpRequestType == "POST") {
                    requestExecutor.executePost(promise.taskRef.requestKey, headers, requestParams)
                } else {
                    requestExecutor.executeGet(requestParams, headers)
                }
                headersObserver.propagateHeaders(requestTypeIdentifier, result.second)
                responseString = result.first
            } catch (e: IOException) {
                throw NetworkException(e)
            }

            val stringResult = SmartConverter.getStringResultOrThrow(converter, responseString, conversionStrategy)
            val result = converter.deserialize(stringResult, resultType)
            validator.validate(result)

            promise.setResult(result, stringResult, invocationBlockType)
        } catch (e: ClearNetworkException) {
            promise.setError(e, invocationBlockType)
        }
    }

    private fun groupByBatchSize(promises: List<CoreTask.Promise>) {
        val executingList = mutableListOf<CoreTask.Promise>()
        var max = promises[0].taskRef.postParams.maxBatchSize

        promises.forEach {
            if (executingList.size < max && it.taskRef.postParams.maxBatchSize > executingList.size) {
                executingList += it
                if (it.taskRef.postParams.maxBatchSize < max) max = it.taskRef.postParams.maxBatchSize
            } else {
                it.setNextIndex(InvocationBlockType.GET_FROM_NET)
            }
        }

        trimAndExecuteOnSingleExecutor(executingList)
    }

    private fun trimAndExecuteOnSingleExecutor(promises: List<CoreTask.Promise>) {
        val maxBatchSize = promises[0].taskRef.postParams.maxBatchSize
        if (promises.size > maxBatchSize) {
            val runningList = promises.subList(0, maxBatchSize)
            val overflowList = promises.subList(maxBatchSize, promises.size)
            overflowList.forEach { it.setNextIndex(InvocationBlockType.GET_FROM_NET) }
            executeSequenceOnSingleExecutor(runningList)
        } else {
            executeSequenceOnSingleExecutor(promises)
        }
    }

    private fun executeSequenceOnSingleExecutor(promises: List<CoreTask.Promise>) {
        if (promises.size == 1) { // in case of maxBatchSize == 1
            obtainFromNet(promises[0])
            return
        }
        try {
            val result: String
            val tasksToWork = promises.toMutableList()
            try {
                val conflictedHeadersTasks = mutableListOf<CoreTask.Promise>()
                val combinedHeaders = combineHeaders(promises, conflictedHeadersTasks)
                conflictedHeadersTasks.forEach { it ->
                    it.setNextIndex(InvocationBlockType.GET_FROM_NET)
                }
                tasksToWork.removeAll(conflictedHeadersTasks)

                val responseWithHeaders = tasksToWork[0].taskRef.postParams.requestExecutor.executePost(
                        createBatchString(tasksToWork),
                        combinedHeaders,
                        mapOf("applicationMethod" to combineRpcMethods(promises))
                )
                result = responseWithHeaders.first
                tasksToWork.forEach { headersObserver.propagateHeaders(it.taskRef.getRequestIdentifier(), responseWithHeaders.second) }
            } catch (e: IOException) {
                throw NetworkException(e)
            }
            getRequestResponseList(tasksToWork, result).forEach {
                try {
                    val stringResult = SmartConverter.getStringResultOrThrow(converter, it.second, it.first.taskRef.postParams.conversionStrategy)
                    val convertedResult = converter.deserialize(stringResult, it.first.taskRef.postParams.resultType)

                    validator.validate(convertedResult)
                    it.first.setResult(convertedResult, stringResult, invocationBlockType)
                } catch (e: ClearNetworkException) {
                    it.first.setError(e, invocationBlockType)
                }
            }
        } catch (e: ClearNetworkException) {
            promises.forEach { task ->
                task.setError(e, invocationBlockType)
            }
        }
    }

    private fun combineHeaders(
            promises: List<CoreTask.Promise>,
            conflictedHeadersTasks: MutableList<CoreTask.Promise>
    ): Map<String, String> = mutableMapOf<String, String>().apply {
        promises.forEach { promise ->
            promise.taskRef.postParams.headers.entries.forEach {
                if (it.key in keys && this[it.key] != it.value) {
                    conflictedHeadersTasks.add(promise)
                } else {
                    this[it.key] = it.value
                }
            }
        }
    }

    @Deprecated("")
    // todo move this logic to Post params
    // It's difficult because it uses strange protocol with comma instead of HTTP params array
    private fun combineRpcMethods(promises: List<CoreTask.Promise>) = promises.joinToString(",") { it.taskRef.getRequestIdentifier() }

    @Throws(ConversionException::class)
    private fun createBatchString(promises: List<CoreTask.Promise>): String {
        return converter.serialize(promises.map { it.taskRef.postParams.requestBody })
    }

    @Throws(ConversionException::class)
    private fun getRequestResponseList(promises: List<CoreTask.Promise>, source: String): List<Pair<CoreTask.Promise, JSONObject>> {
        try {
            val array = JSONArray(source)
            return (0 until array.length())
                    .map { array.getJSONObject(it) }
                    .map { getTaskPromiseById(promises, it.getLong("id")) to it }
        } catch (e: JSONException) {
            throw ConversionException("Incorrect batch response: $source", e)
        }

    }

    @Throws(ConversionException::class)
    private fun getTaskPromiseById(promises: List<CoreTask.Promise>, id: Long): CoreTask.Promise {
        try {
            return promises.first {
                // todo remove manual casting
                (it.taskRef.postParams.requestBody as RPCRequest).id == id
            }
        } catch (e: NoSuchElementException) {
            throw ConversionException("Responses ids not comparable with requests ids", e)
        }
    }
}