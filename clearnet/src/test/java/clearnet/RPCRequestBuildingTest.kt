package clearnet

import clearnet.help.*
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class RPCRequestBuildingTest {

    val testConverterExecutor = TestConverterExecutor()
    val testRequests: TestRequests = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
            .create(TestRequests::class.java, RequestExecutorStub(), Int.MAX_VALUE)


    @Before
    fun prepare() {
        testConverterExecutor.lastParams = null
    }


    @Test
    fun rpcMethodNaming() {
        try {
            testRequests.withoutRPCAnnotation()
            fail("Exception must be thrown without any RPC annotation")
        } catch (ignored: IllegalArgumentException) {
        }

        testRequests.test()
        assertNotNull(testConverterExecutor.lastParams)
        assertNotNull(testConverterExecutor.lastParams!!.requestBody)
        var body = testConverterExecutor.lastParams!!.requestBody as RPCRequest
        assertEquals("testScope.test", body.method)

        testRequests.test2()
        assertNotNull(testConverterExecutor.lastParams)
        assertNotNull(testConverterExecutor.lastParams!!.requestBody)
        body = testConverterExecutor.lastParams!!.requestBody as RPCRequest
        assertEquals("testScope.test", body.method)
    }

    @Test
    fun rpcRequestWrongArguments() {
        try {
            testRequests.multipleBody("b1", "b2")
            fail("Exception must be thrown if multiple body")
        } catch (ignored: IllegalStateException) {}

        try {
            testRequests.unknownArgs("arg")
            fail("Exception must be thrown if parameter was annotated")
        } catch (ignored: IllegalArgumentException) {}

        try {
            testRequests.paramsAndBodyMixing(1, "p2")
            fail("Exception must be thrown if body and parameter were mixed")
        } catch (ignored: IllegalStateException) {}

        try {
            testRequests.paramsAndBodyMixingOnSingleArgument(1)
            fail("Exception must be thrown if body and parameter were mixed")
        } catch (ignored: IllegalStateException) {}
    }

    @Test
    fun rpcRequestRightBuilding() {
        val p3 = intArrayOf(1, 2)
        testRequests.testParams("t1", 1, p3)
        assertNotNull(testConverterExecutor.lastParams)
        assertNotNull(testConverterExecutor.lastParams?.requestBody)
        assertNotNull((testConverterExecutor.lastParams!!.requestBody as? RPCRequest)?.params)
        val paramsMap: HashMap<String, Any> = (testConverterExecutor.lastParams!!.requestBody as RPCRequest).params as HashMap<String, Any>
        assertEquals("t1", paramsMap["p1"])
        assertEquals(1, paramsMap["p2"])
        assertEquals(p3, paramsMap["p3"])

        testConverterExecutor.lastParams = null

        testRequests.testBody(p3)
        assertNotNull(testConverterExecutor.lastParams)
        assertNotNull(testConverterExecutor.lastParams?.requestBody)
        assertEquals(p3, (testConverterExecutor.lastParams!!.requestBody as RPCRequest).params)
    }

    @Test
    fun rpcScopeOnFile() {
        val testRequest: TestRequestsForSingleScope = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequestsForSingleScope::class.java, RequestExecutorStub(), Int.MAX_VALUE)


        testRequest.tryIt()

        assertNotNull(testConverterExecutor.lastParams)
        assertNotNull(testConverterExecutor.lastParams!!.requestBody)
        val body = testConverterExecutor.lastParams!!.requestBody as RPCRequest
        assertEquals("test.tryIt", body.method)
    }
}