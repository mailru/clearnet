package clearnet.android

import clearnet.ExecutorWrapper
import clearnet.android.help.*
import clearnet.interfaces.RequestCallback
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.am.kutils.consume
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallbackHolderTest {
    val testConverterExecutor = TestConverterExecutor()
    var callbackHolder = CallbackHolder(ImmediateExecutor)
    val testRequests = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
            .create(TestRequests::class.java, RequestExecutorStub(), 1, callbackHolder)

    @Before
    fun prepare() {
        testConverterExecutor.lastParams = null
        callbackHolder.callbackList.clear()
    }

    @Test
    fun testCallbackManualClear() {
        val callback = TestCallback<Any?>()

        testRequests.requestWithCallback(callback)
        assertEquals(1, callbackHolder.callbackList.size)
        val params = testConverterExecutor.lastParams!!

        callbackHolder.clear()
        assertTrue(callbackHolder.callbackList.isEmpty())

        params.subject.onNext(1)
        assertFalse(callback.called)
        assertTrue(callbackHolder.callbackList.isEmpty())
    }

    @Test
    fun testDisposablesManualClear() {
        val testObserver = TestObserver<Any>()

        testRequests.reactiveRequest().subscribe(testObserver)

        assertEquals(1, callbackHolder.disposables.size())
        val params = testConverterExecutor.lastParams!!

        callbackHolder.clear()
        assertEquals(0, callbackHolder.disposables.size())

        params.subject.onNext(1)
        assertFalse(testObserver.called)
        assertEquals(0, callbackHolder.disposables.size())
    }

    @Test
    fun testWrapperCast(){

        open class T1 : RequestCallback<String> {
            override fun onSuccess(response: String) {
            }

            override fun onFailure(exception: clearnet.error.ClearNetworkException) {
            }
        }

        var rc: RequestCallback<String> = callbackHolder.wrap(T1(), RequestCallback::class.java)

        rc.onSuccess("ignored")
        rc.onFailure(clearnet.error.ResponseErrorException("ignored"))

        class T2 : T1(), Runnable {
            override fun run() {}
        }

        rc = callbackHolder.wrap(T2(), RequestCallback::class.java)

        val r: Runnable = callbackHolder.wrap(T2(), Runnable::class.java)

        rc.onSuccess("ignored")
        rc.onFailure(clearnet.error.ResponseErrorException("ignored"))

        rc = callbackHolder.createEmpty(RequestCallback::class.java)

        rc.onSuccess("ignored")
        rc.onFailure(clearnet.error.ResponseErrorException("ignored"))
    }

    @Test
    fun testExecuteOnCorrectExecutor(){
        val callFlag = AtomicInteger()
        val runFlag = AtomicInteger()

        val callbackHolder = clearnet.android.CallbackHolder(Executor {
            runFlag.incrementAndGet()
            it.run()
        })

        var testClass: Runnable? = Runnable { callFlag.incrementAndGet() }

        val testWrapped = callbackHolder.wrap(testClass!!, Runnable::class.java)


        testWrapped.run()

        assertEquals(1, callFlag.get())
        assertEquals(1, runFlag.get())

        callFlag.set(0)
        runFlag.set(0)

        val reference = WeakReference(testClass)

        testClass = null
        callbackHolder.clear()

        val started = System.currentTimeMillis()

        while (reference.get() != null && System.currentTimeMillis() - started < 2000) {
            System.gc()
        }

        testWrapped.run()

        assertEquals(0, callFlag.get())
        assertEquals(0, runFlag.get())

        assertNull(reference.get())
    }

    @Test
    fun testExecuteOnCorrectScheduler() {
        val testExecutor = TestExecutor()
        val specialCallbackHolder = CallbackHolder(testExecutor)
        val specialTestRequests = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, RequestExecutorStub(), 1, specialCallbackHolder)

        val testObserver = TestObserver<Any>()

        specialTestRequests.reactiveRequest().subscribe(testObserver)
        val params = testConverterExecutor.lastParams!!

        params.subject.onNext(1)

        assertFalse(testObserver.called)

        testExecutor.run()

        assertTrue(testObserver.called)
    }

    internal class TestCallback<T> : RequestCallback<T> {
        var called = false
        var errorCalled = false

        override fun onSuccess(response: T) {
            called = true
        }

        override fun onFailure(exception: clearnet.error.ClearNetworkException) {
            errorCalled = true
        }
    }

    internal class TestObserver<T> : Observer<T> {
        var called = false
        var errorCalled = false

        override fun onNext(t: T) {
            called = true
        }

        override fun onError(e: Throwable?) {
            errorCalled = true
        }

        override fun onComplete() {}
        override fun onSubscribe(d: Disposable?) {}
    }

    private class TestExecutor : Executor {
        private val tasks: Queue<Runnable> = LinkedList<Runnable>()

        override fun execute(command: Runnable?) {
            tasks += command
        }

        fun run() = tasks.consume(Runnable::run)
    }
}