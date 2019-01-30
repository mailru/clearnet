package clearnet

import clearnet.error.ClearNetworkException
import clearnet.help.*
import clearnet.interfaces.*
import com.google.gson.Gson
import io.reactivex.schedulers.TestScheduler
import org.json.JSONArray
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

class BatchRequestTest : CoreBlocksTest() {

    companion object {
        private const val MAX_BATCH_SIZE = 5
    }

    private lateinit var core: Core
    private lateinit var invocationBlocks: TestCoreBlocks

    @Before
    fun setup() {
        invocationBlocks = TestCoreBlocks(
                cacheProvider = testCacheProvider
        )

        timeT = invocationBlocks.getFromNetTimeThreshold

        core = Core(
                ioExecutor = TrampolineExecutor(),
                worker = testScheduler,
                blocks = *invocationBlocks.getAll()
        )
    }

    @Test
    fun creatingBatch() {
        val firstResult = AtomicReference<String>()
        val secondResult = AtomicReference<String>()

        val testRequests = provideTestRequests(BatchTestRequestExecutor())

        testRequests.firstOfBatch(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                firstResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })
        testRequests.secondOfBatch(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                secondResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })

        forwardScheduler()

        assertEquals("test0", firstResult.get())
        assertEquals("test1", secondResult.get())
    }

    @Test
    fun differentExecutors() {
        val firstRequestExecutor = TestSingleRequestsExecutor("test1")
        val secondRequestExecutor = TestSingleRequestsExecutor("test2")

        val firstRequests = provideTestRequests(firstRequestExecutor)
        val secondRequests = provideTestRequests(secondRequestExecutor)

        val firstResult = AtomicReference<String>()
        val secondResult = AtomicReference<String>()

        firstRequests.firstOfBatch(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                firstResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })

        secondRequests.secondOfBatch(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                secondResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })

        forwardScheduler()
        forwardScheduler()

        assertEquals("test1", firstResult.get())
        assertEquals("test2", secondResult.get())

        assertEquals(1, firstRequestExecutor.called)
        assertEquals(1, secondRequestExecutor.called)
    }


    @Test
    fun headers() {
        val counter = AtomicInteger()
        val header = AtomicReference<String>()
        invocationBlocks.getHeadersObserver().register("test.secondOfBatch", object : HeaderListener {
            override fun onNewHeader(method: String, name: String, value: String) {
                counter.incrementAndGet()
                header.set(value)
            }
        }, "testHeader")

        val testRequests = provideTestRequests(BatchTestRequestExecutor())
        testRequests.firstOfBatch(RequestCallbackStub())
        testRequests.secondOfBatch(RequestCallbackStub())

        forwardScheduler()

        assertEquals(1, counter.get())
        assertEquals("test", header.get())
    }

    @Test
    fun testConflictedHeaders() {
        val executor = TestCheckBatchSizeRequestExecutor()
        val testRequests = provideTestRequests(
                requestExecutor = executor,
                headerProvider = object : HeaderProvider {
                    var callsCount = 0
                    override fun obtainHeadersList(): Map<String, String> {
                        callsCount++
                        return when (callsCount) {
                            1 -> mapOf("Header" to "header-1")
                            2 -> mapOf("Header" to "header-2")
                            else -> emptyMap()
                        }
                    }
                }
        )
        testRequests.firstOfBatch(RequestCallbackStub())
        testRequests.secondOfBatch(RequestCallbackStub())

        forwardScheduler()
        forwardScheduler()

        assertEquals(2, executor.counter.size)
    }

    @Test
    fun someOfBatchIsFromCache() {
        val firstResult = AtomicReference<String>()
        val secondResult = AtomicReference<String>()

        val testRequests = provideTestRequests(TestSingleRequestsExecutor("test1"))
        testRequests.firstOfBatch(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                firstResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })
        testRequests.forBatchWithPriorityCache(object : RequestCallback<String> {
            override fun onSuccess(response: String) {
                secondResult.set(response)
            }

            override fun onFailure(exception: ClearNetworkException) {
                throw exception
            }
        })

        forwardScheduler()

        assertEquals("test1", firstResult.get())
        assertEquals("cache", secondResult.get())
    }

    @Test
    fun moreThenMaxBatchSizeRequests() {
        val executor = TestCheckBatchSizeRequestExecutor()
        val testRequests = provideTestRequests(executor)

        testRequests.firstOfBatch(RequestCallbackStub())

        for (i in 0 until MAX_BATCH_SIZE) {
            testRequests.firstOfBatch(RequestCallbackStub())
        }

//        testScheduler.triggerActions()
        testScheduler.advanceTimeBy(201, TimeUnit.MILLISECONDS)

        assertEquals(2, executor.counter.size)
        assertEquals(MAX_BATCH_SIZE, executor.counter[0])
        assertEquals(1, executor.counter[1])
    }

    @Test
    fun noBatchTest() { // also tests cases with different batch sizes on each task in same request executor
        val executor = TestCheckBatchSizeRequestExecutor()
        val testRequests = provideTestRequests(executor)

        testRequests.firstOfBatch(RequestCallbackStub())
        testRequests.batchNoBatch(RequestCallbackStub())
        testRequests.secondOfBatch(RequestCallbackStub())

        testScheduler.triggerActions()
        testScheduler.advanceTimeBy(201, TimeUnit.MILLISECONDS)

        assertEquals(2, executor.counter.size)
        assertEquals(2, executor.counter[0])
        assertEquals(1, executor.counter[1])
    }

    private fun provideTestRequests(requestExecutor: IRequestExecutor, headerProvider: HeaderProvider = HeadersProviderStub): TestRequests {
        return ExecutorWrapper(core, headerProvider, GsonTestSerializer())
                .create(TestRequests::class.java, requestExecutor, MAX_BATCH_SIZE)
    }


    private object testCacheProvider : ICacheProvider {
        override fun store(key: String, value: String, expiresAfter: Long) {}
        override fun obtain(key: String) = "cache"
    }

    private class TestCheckBatchSizeRequestExecutor : BatchTestRequestExecutor() {
        var counter = mutableListOf<Int>()
        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
            if(body.startsWith("{")) {
                counter.add(1)
            } else {
                val array = JSONArray(body)
                counter.add(array.length())
            }
            return super.executePost(body, headers, queryParams)
        }
    }

    private class TestSingleRequestsExecutor(private val result: String) : RequestExecutorStub() {
        var called = 0

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
            called++
            return Pair(Gson().toJson(mapOf("result" to result)), emptyMap())
        }
    }
}