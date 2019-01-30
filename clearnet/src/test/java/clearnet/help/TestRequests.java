package clearnet.help;

import annotations.Body;
import annotations.ConversionStrategy;
import annotations.NotBindable;
import annotations.Parameter;
import annotations.RPCMethod;
import annotations.RPCMethodScope;
import clearnet.annotations.InvocationStrategy;
import clearnet.annotations.NoBatch;
import clearnet.conversion.InnerErrorConversionStrategy;
import clearnet.conversion.InnerResultConversionStrategy;
import clearnet.interfaces.RequestCallback;
import io.reactivex.Observable;

import static clearnet.InvocationStrategy.AUTHORIZED_REQUEST;
import static clearnet.InvocationStrategy.PRIORITY_CACHE;
import static clearnet.InvocationStrategy.PRIORITY_REQUEST;
import static clearnet.InvocationStrategy.RETRY_IF_NO_NETWORK;

public interface TestRequests {

    // ---- RPCRequestBuildingTest ----

    @RPCMethodScope("testScope")
    void test();

    @RPCMethod("testScope.test")
    void test2();

    void withoutRPCAnnotation();

    @RPCMethodScope("test")
    void testParams(@Parameter("p1") String p1, @Parameter("p2") int p2, @Parameter("p3") int[] p3);

    @RPCMethodScope("test")
    void testBody(@Body int[] values);

    @RPCMethodScope("test")
    void multipleBody(@Body String p1, @Body String p2);

    @RPCMethodScope("test")
    void unknownArgs(String arg);

    @RPCMethodScope("test")
    void paramsAndBodyMixing(@Parameter("p1") int p1, @Body String p2);

    @RPCMethodScope("test")
    void paramsAndBodyMixingOnSingleArgument(@Parameter("p1") @Body int p1);

    // ---- BatchRequestTest ----

    @NotBindable
    @RPCMethodScope("test")
    void firstOfBatch(RequestCallback<String> callback);

    @RPCMethodScope("test")
    void secondOfBatch(RequestCallback<String> callback);

    @RPCMethodScope("test")
    @InvocationStrategy(PRIORITY_CACHE)
    void forBatchWithPriorityCache(RequestCallback<String> callback);

    @NotBindable
    @NoBatch
    @RPCMethodScope("test")
    void batchNoBatch(RequestCallback<String> callback);

    // ---- CacheStrategyTest ----

    @RPCMethodScope("test")
    void noCache(RequestCallback<String> requestCallback);

    @InvocationStrategy(PRIORITY_REQUEST)
    @RPCMethodScope("test")
    void priorityRequest(RequestCallback<String> requestCallback);

    @InvocationStrategy(PRIORITY_CACHE)
    @RPCMethodScope("test")
    void priorityCache(RequestCallback<String> requestCallback);



    // ---- SuccessOrErrorResponsesVariantsTest ----
    @RPCMethodScope("test")
    void commonResponse(RequestCallback<TestObject> callback);

    @ConversionStrategy(InnerResultConversionStrategy.class)
    @RPCMethodScope("test")
    void innerResponse(RequestCallback<TestObject> callback);

    @ConversionStrategy(InnerErrorConversionStrategy.class)
    @RPCMethodScope("test")
    void innerErrorResponse(RequestCallback<TestObject> callback);


    // ---- TasksSubscriptionTest ----
    @RPCMethodScope("test")
    void bindableTask(@Parameter("param") int parameter, RequestCallback<String> callback);

    @NotBindable
    @RPCMethodScope("test")
    void notBindableTask(RequestCallback<String> callback);

    @RPCMethodScope("test")
    @InvocationStrategy(PRIORITY_REQUEST)
    void withCacheBindableTask(RequestCallback<String> callback);

    @RPCMethodScope("test")
    @InvocationStrategy({PRIORITY_CACHE, AUTHORIZED_REQUEST, RETRY_IF_NO_NETWORK})
    void mergeStrategiesTest();

    // ---- Reactive ----
    @RPCMethodScope("test")
    Observable<String> reactiveRequest(@Parameter("p1") int p1);
}
