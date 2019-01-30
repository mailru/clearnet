package clearnet

import clearnet.help.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksAutoBindTest : CoreBlocksTest() {
    private lateinit var core: Core
    private lateinit var testRequests: TestRequests
    private lateinit var requestExecutor: TestRequestExecutor

    @Before
    fun setup() {
        core = Core(
                ioExecutor = MultiThreadExecutor,
                blocks = *coreBlocks.getAll()
        )
        requestExecutor = TestRequestExecutor()
        testRequests = ExecutorWrapper(core, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, requestExecutor, 1, CallbackHolderStub)
    }

    @Test()
    fun autoBind() {
        val semaphore = Semaphore(3)
        semaphore.acquire(3)

        val callback = object : RequestCallbackStub<String>() {
            override fun onSuccess(response: String) {
                semaphore.release()
            }
        }

        testRequests.bindableTask(1, callback)
        testRequests.bindableTask(1, callback)
        testRequests.bindableTask(1, callback)

        waitForTasksOnTestRequestExecutor()

        requestExecutor.semaphore.release(3)


        assertTrue(semaphore.tryAcquire(3, 100, TimeUnit.MILLISECONDS), "Callback called ${semaphore.availablePermits()} times")
        assertEquals(1, requestExecutor.counter.get())
    }

    @Test
    fun noBindWithDifferentParams() {
        val semaphore = Semaphore(2)
        semaphore.acquire(2)

        val callback = object : RequestCallbackStub<String>() {
            override fun onSuccess(response: String) {
                semaphore.release()
            }
        }
        testRequests.bindableTask(1, callback)
        testRequests.bindableTask(2, callback)

        waitForTasksOnTestRequestExecutor()

        requestExecutor.semaphore.release(2)

        assertTrue(semaphore.tryAcquire(2, 100, TimeUnit.MILLISECONDS))

        assertEquals(2, requestExecutor.counter.get())
    }

    @Test
    fun noBindNotBindable() {
        val semaphore = Semaphore(2)
        semaphore.acquire(2)

        val callback = object : RequestCallbackStub<String>() {
            override fun onSuccess(response: String) {
                semaphore.release()
            }
        }

        testRequests.notBindableTask(callback)
        testRequests.notBindableTask(callback)

        waitForTasksOnTestRequestExecutor()

        requestExecutor.semaphore.release(2)

        assertTrue(semaphore.tryAcquire(2, 100, TimeUnit.MILLISECONDS))
        assertEquals(2, requestExecutor.counter.get())
    }


    private fun waitForTasksOnTestRequestExecutor() {
        var time = 0
        do {
            Thread.sleep(10L)
            time += 10
        } while (!requestExecutor.semaphore.hasQueuedThreads())
        System.out.println("Waiting finished for $time milliseconds")
    }

    private open class TestRequestExecutor : BatchTestRequestExecutor() {
        var counter = AtomicInteger()
        var semaphore = Semaphore(3).apply {
            acquire(3)
        }

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
            semaphore.acquire()
            val result = super.executePost(body, headers, queryParams)
            counter.incrementAndGet()
            return result
        }
    }
}