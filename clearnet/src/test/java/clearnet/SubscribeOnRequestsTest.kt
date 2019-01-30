package clearnet

import clearnet.help.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class SubscribeOnRequestsTest : CoreBlocksTest() {
    private lateinit var core: Core
    private lateinit var testRequests: TestRequests

    @Before
    fun setup() {
        core = Core(
                ioExecutor = ImmediateExecutor,
                worker = testScheduler,
                blocks = *coreBlocks.getAll()
        )
        testRequests = ExecutorWrapper(core, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, BatchTestRequestExecutor(), 1, CallbackHolderStub)
    }

    @Test
    fun subscribe() {
        val successes = AtomicInteger()
        val subscriptionCalled = AtomicInteger()

        val callback = object : RequestCallbackStub<String>() {
            override fun onSuccess(response: String) {
                successes.incrementAndGet()
            }
        }

        val subscription = core.subscribe("test.bindableTask", object : RequestCallbackStub<String>() {
            override fun onSuccess(response: String) {
                subscriptionCalled.incrementAndGet()
            }
        })

        testRequests.bindableTask(1, callback)
        forwardScheduler()
        assertEquals(1, successes.get())
        assertEquals(1, subscriptionCalled.get())

        testRequests.notBindableTask(callback)
        forwardScheduler()
        assertEquals(2, successes.get())
        assertEquals(1, subscriptionCalled.get())

        testRequests.bindableTask(2, callback)
        forwardScheduler()
        assertEquals(3, successes.get())
        assertEquals(2, subscriptionCalled.get())

        subscription.unsubscribe()

        testRequests.bindableTask(3, callback)
        forwardScheduler()
        assertEquals(4, successes.get())
        assertEquals(2, subscriptionCalled.get())
    }
}