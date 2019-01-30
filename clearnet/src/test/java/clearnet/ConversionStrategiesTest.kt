package clearnet

import clearnet.ConversionStrategiesTest.CallbackState.*
import clearnet.interfaces.ISerializer
import clearnet.help.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import clearnet.interfaces.RequestCallback
import clearnet.model.RpcErrorResponse
import clearnet.model.RpcInnerError
import clearnet.model.RpcInnerErrorResponse
import io.reactivex.schedulers.TestScheduler
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(Parameterized::class)
class ConversionStrategiesTest(
        var callbackState: CallbackState
) : CoreBlocksTest() {

    private lateinit var testRequestExecutor: TestRequestExecutor
    private lateinit var converterExecutor: Core
    private lateinit var testRequests: TestRequests
    private lateinit var callback: TestCallback

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any?>> {
            val d: ArrayList<Array<Any?>> = ArrayList() // I was confused with generic typing problems =(
            d.add(arrayOf(COMMON_OBJECT))
            d.add(arrayOf(INNER_OBJECT))
            d.add(arrayOf(COMMON_ERROR))
            d.add(arrayOf(INNER_ERROR_ARRAY))
            d.add(arrayOf(INNER_ERROR))
            return d
        }
    }

    @Before
    fun setup(){
        testRequestExecutor = TestRequestExecutor()
        converterExecutor = Core(ImmediateExecutor, testScheduler, blocks = *coreBlocks.getAll())
        testRequests = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, testRequestExecutor, Int.MAX_VALUE)
        callback =  TestCallback()
    }


    @Before
    fun clean() {
        callback.testObject = null
        callback.exception = null
        testRequestExecutor.callbackState = callbackState
    }

    @Test
    fun test() {

        when (callbackState) {
            COMMON_OBJECT, COMMON_ERROR -> testRequests.commonResponse(callback)
            INNER_OBJECT, INNER_ERROR_ARRAY -> testRequests.innerResponse(callback)
            INNER_ERROR -> testRequests.innerErrorResponse(callback)
        }

        forwardScheduler()

        when (callbackState) {
            COMMON_OBJECT, INNER_OBJECT -> {
                assertNotNull(callback.testObject)
                assertEquals(1, callback.testObject!!.test)
                assertNull(callback.exception)
            }
            else -> {
                assertNull(callback.testObject)
                assertNotNull(callback.exception)

                val responseError = callback.exception as clearnet.error.ResponseErrorException
                assertNotNull(responseError.error)
                when (callbackState) {
                    COMMON_ERROR -> {
                        val rpcErrorResponse = responseError.error as RpcErrorResponse
                        assertEquals(1, rpcErrorResponse.code)
                        assertEquals("test", rpcErrorResponse.message)
                    }
                    INNER_ERROR_ARRAY -> {
                        val errorArray = responseError.error as Array<RpcInnerError>
                        assertEquals(1, errorArray.size)
                        val rpcInnerError = errorArray[0]
                        assertEquals("test", rpcInnerError.error)
                        assertEquals("test", rpcInnerError.message)
                    }
                    INNER_ERROR -> {
                        val innerError = responseError.error as RpcInnerErrorResponse
                        assertEquals("test", innerError.error)
                    }
                    else -> {
                        fail("Unexpected branch")
                    }
                }
            }
        }
    }


    class TestRequestExecutor : RequestExecutorStub() {
        private val converter: ISerializer by lazy { GsonTestSerializer() }
        lateinit var callbackState: CallbackState

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
            val response =  when (callbackState) {
                COMMON_OBJECT -> "{\"result\":{\"test\":1}}"
                INNER_OBJECT -> "{\"result\":{\"success\":true,\"data\":{\"test\":1}}}"
                COMMON_ERROR -> "{\"error\":" + converter.serialize(RpcErrorResponse(1, "test", null)) + "}"
                INNER_ERROR_ARRAY -> "{\"result\":{\"success\":false,\"errors\":[" + converter.serialize(RpcInnerError("test", "test")) + "]}}"
                INNER_ERROR -> "{\"result\":" + converter.serialize(RpcInnerErrorResponse("test", "Test description")) + "}"
                else -> "ERROR"
            }
            return Pair(response, Collections.emptyMap())
        }
    }

    class TestCallback : RequestCallback<TestObject> {
        var testObject: TestObject? = null
        var exception: clearnet.error.ClearNetworkException? = null

        override fun onSuccess(response: TestObject) {
            testObject = response
        }

        override fun onFailure(exception: clearnet.error.ClearNetworkException) {
            if (exception !is clearnet.error.ResponseErrorException) throw exception
            this.exception = exception
        }
    }

    enum class CallbackState {
        COMMON_OBJECT, INNER_OBJECT, COMMON_ERROR, INNER_ERROR, INNER_ERROR_ARRAY
    }
}