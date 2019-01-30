package clearnet

import clearnet.InvocationBlockType.*
import clearnet.help.*
import clearnet.interfaces.IConverterExecutor
import clearnet.interfaces.Subscription
import clearnet.model.PostParams
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrategyMergerTest {
    @Test
    fun mergeTest() {
        val lastPostParams = AtomicReference<PostParams>()

        val converterExecutor = object : IConverterExecutor {
            override fun executePost(postParams: PostParams) {
                lastPostParams.set(postParams)
            }
        }

        val executorWrapper = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
        val testRequests = executorWrapper.create(TestRequests::class.java, BatchTestRequestExecutor(), Int.MAX_VALUE)

        testRequests.mergeStrategiesTest()

        assertNotNull(lastPostParams.get())

        val strategy = lastPostParams.get().invocationStrategy

        assertTrue(Arrays.deepEquals(strategy[INITIAL][true], arrayOf(CHECK_AUTH_TOKEN)))
        assertTrue(Arrays.deepEquals(strategy[INITIAL][false], emptyArray()))
        assertTrue(Arrays.deepEquals(strategy[CHECK_AUTH_TOKEN][true], arrayOf(GET_FROM_CACHE)))
        assertTrue(Arrays.deepEquals(strategy[CHECK_AUTH_TOKEN][false], arrayOf(DELIVER_ERROR)))
        assertTrue(Arrays.deepEquals(strategy[GET_FROM_CACHE][true], arrayOf(DELIVER_RESULT)))
        assertTrue(Arrays.deepEquals(strategy[GET_FROM_CACHE][false], arrayOf(GET_FROM_NET)))

        assertEquals("true", strategy["retry_network_error"])
    }
}