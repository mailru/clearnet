package clearnet

import clearnet.interfaces.HeaderListener
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class SimpleHeaderObserverTest {

    private lateinit var headerObserver: SimpleHeadersObserver
    private val testHeaders = mapOf(
            "n1" to "v1",
            "n2" to "v2"
    )


    @Before
    fun setup(){
        headerObserver = SimpleHeadersObserver()
    }


    @Test
    fun headersDelivered(){
        val called = AtomicInteger()
        headerObserver.register("test", object : HeaderListener {
            override fun onNewHeader(method: String, name: String, value: String) {
                called.incrementAndGet()
                assertEquals("test", method)
            }
        })

        headerObserver.propagateHeaders("test", testHeaders)
        headerObserver.propagateHeaders("test2", testHeaders)

        assertEquals(2, called.get())

        headerObserver.propagateHeaders("test", testHeaders)

        assertEquals(4, called.get())
    }

    @Test
    fun headerFiltering(){
        val called = AtomicInteger()
        headerObserver.register("test", object : HeaderListener {
            override fun onNewHeader(method: String, name: String, value: String) {
                called.incrementAndGet()
                assertEquals("test", method)
                assertEquals("n1", name)
            }
        }, "n1")

        headerObserver.propagateHeaders("test", testHeaders)
        headerObserver.propagateHeaders("test2", testHeaders)

        assertEquals(1, called.get())
    }

    @Test
    fun unsubscription(){
        val called = AtomicInteger()
        val subscription = headerObserver.register("test", object : HeaderListener {
            override fun onNewHeader(method: String, name: String, value: String) {
                called.incrementAndGet()
            }
        })
        headerObserver.propagateHeaders("test", testHeaders)

        subscription.unsubscribe()

        headerObserver.propagateHeaders("test", testHeaders)

        assertEquals(2, called.get())
    }
}