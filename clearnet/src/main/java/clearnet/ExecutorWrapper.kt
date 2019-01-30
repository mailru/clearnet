package clearnet

import clearnet.annotations.*
import clearnet.annotations.Parameter
import clearnet.annotations.NoBatch
import clearnet.annotations.RPCMethodScope
import clearnet.conversion.DefaultConversionStrategy
import clearnet.error.ClearNetworkException
import clearnet.interfaces.*
import clearnet.interfaces.ConversionStrategy
import clearnet.model.MergedInvocationStrategy
import clearnet.model.RpcPostParams
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.Subject
import java.lang.reflect.*


class ExecutorWrapper(private val converterExecutor: IConverterExecutor,
                      private val headerProvider: HeaderProvider,
                      private val serializer: ISerializer) {
    private val defaultCallbackHolder: ICallbackHolder

    fun <T> create(tClass: Class<T>, requestExecutor: IRequestExecutor, maxBatchSize: Int, callbackHolder: ICallbackHolder = defaultCallbackHolder): T {
        return Proxy.newProxyInstance(tClass.classLoader, arrayOf<Class<*>>(tClass), ApiInvocationHandler(
                requestExecutor,
                callbackHolder,
                tClass.getAnnotation(RPCMethodScope::class.java)?.value,
                maxBatchSize
        )) as T
    }


    private inner class ApiInvocationHandler(
            private val requestExecutor: IRequestExecutor,
            private val callbackHolder: ICallbackHolder,
            private val rpcMethodScope: String?,
            private val maxBatchSize: Int
    ) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val requestBody = RPCRequest(retrieveRemoteMethod(method))

            addDefaultParameterIfExists(method, requestBody)
            val (invocationStrategy: MergedInvocationStrategy, expiresAfter: Long) = retrieveInvocationStrategyAndExpiration(method)
            val (requestCallback, type) = fillRequestBodyAndFindListeners(args, method, requestBody)

            val postParams = RpcPostParams(
                    generateAmruRequestParams(requestBody),
                    requestBody,
                    type,
                    requestExecutor,
                    invocationStrategy,
                    expiresAfter,
                    retrieveConversionStrategy(method),
                    headerProvider.obtainHeadersList(),
                    method.getAnnotation(NotBindable::class.java) == null,
                    getMaxBatchSize(method),
                    serializer
            )


            wrapCallback(requestCallback, postParams.subject, callbackHolder)

            // todo check callback and observable
            converterExecutor.executePost(postParams)

            return if(isReturnsObservable(method)) postParams.subject.observeOn(callbackHolder.scheduler).doOnSubscribe(callbackHolder::hold)
            else null
        }

        private fun wrapCallback(requestCallback: RequestCallback<Any?>?, subject: Subject<Any>, callbackHolder: ICallbackHolder): RequestCallback<Any?>? {
            return if (requestCallback == null) {
                null
            } else {
                val result = callbackHolder.wrap(requestCallback, RequestCallback::class.java)
                subject.subscribe({
                    result.onSuccess(it)
                }, {
                    result.onFailure(it as ClearNetworkException)
                })
                result
            }
        }

        private fun retrieveRemoteMethod(method: Method): String {
            val methodAnnotation = method.getAnnotation(RPCMethod::class.java)
            if (methodAnnotation != null) {
                return methodAnnotation.value
            }

            val scope = method.getAnnotation(RPCMethodScope::class.java)?.value ?: rpcMethodScope

            if(scope != null) {
                return if(scope.isEmpty()) method.name else "$scope.${method.name}"
            } else {
                throw IllegalArgumentException("Method " + method.name + " must be annotated with @" + RPCMethod::class.java.name + " or @" + RPCMethodScope::class.java.name + " annotation")
            }
        }

        private fun isReturnsObservable(method: Method) = method.returnType == Observable::class.java

        private fun getGenericParameterType(method: Method, parameterIndex: Int): Type {
            return (method.genericParameterTypes[parameterIndex] as ParameterizedType).actualTypeArguments[0]
        }

        private fun getGenericReturnType(method: Method): Type {
            return (method.genericReturnType as ParameterizedType).actualTypeArguments[0]
        }

        private fun fillRequestBodyAndFindListeners(args: Array<out Any>?, method: Method, requestBody: RPCRequest): Pair<RequestCallback<Any?>?, Type> {
            var requestCallback: RequestCallback<Any?>? = null

            var callbackIndex = -1
            if (args != null) {
                val annotations = method.parameterAnnotations

                for (i in args.indices) {
                    var parameterSet = false
                    var bodySet = false
                    for (annotation in annotations[i]) {
                        if (annotation is Parameter) {
                            requestBody.addParameter(annotation.value, args[i])
                            parameterSet = true
                        } else if (annotation is Body) {
                            requestBody.setParamsBody(args[i])
                            bodySet = true
                        }
                    }

                    if (parameterSet || bodySet) continue

                    if (args[i] is RequestCallback<*>) {
                        requestCallback = args[i] as RequestCallback<Any?>
                        callbackIndex = i
                    } else {
                        throw IllegalArgumentException("All parameters in method " + method.name + " must have the Parameter annotation")
                    }
                }
            }
            val type = when {
                callbackIndex >= 0 -> getGenericParameterType(method, callbackIndex)
                isReturnsObservable(method) -> getGenericReturnType(method)
                else -> method.getAnnotation(ResultType::class.java)?.value?.java as Type? ?: Any::class.java
            }
            return Pair(requestCallback, type)
        }

        private fun addDefaultParameterIfExists(method: Method, requestBody: RPCRequest) {
            val defaultParameters = retrieveDefaultParameters(method)
            defaultParameters.forEach {
                requestBody.addParameter(it.key, it.value)
            }
        }

        private fun retrieveDefaultParameters(method: Method): Array<DefaultParameter> {
            val defaultParameter = method.getAnnotation(DefaultParameter::class.java)
            val defaultParameters = method.getAnnotation(DefaultParameters::class.java)

            if (defaultParameter == null || defaultParameters == null) {
                return if (defaultParameter == null) defaultParameters?.value ?: arrayOf() else arrayOf(defaultParameter)
            } else {
                throw IllegalArgumentException("Method ${method.name} must have either DefaultParameter annotation or DefaultParameters but not both")
            }
        }

        private fun retrieveConversionStrategy(method: Method): ConversionStrategy {
            val conversionStrategyAnnotation = method.getAnnotation(clearnet.annotations.ConversionStrategy::class.java)
            return if (conversionStrategyAnnotation == null) {
                DefaultConversionStrategy()
            } else {
                val result: ConversionStrategy = conversionStrategyAnnotation.value.java.newInstance() as ConversionStrategy
                result.init(conversionStrategyAnnotation.parameter)
                result
            }
        }

        private fun retrieveInvocationStrategyAndExpiration(method: Method): Pair<MergedInvocationStrategy, Long> {
            var invocationStrategies: Array<InvocationStrategy> = emptyArray()
            var expiresAfter: Long = 0
            val annotation = method.getAnnotation(clearnet.annotations.InvocationStrategy::class.java)
            if (annotation != null) {
                invocationStrategies = annotation.value
                expiresAfter = annotation.cacheExpiresAfter
            }

            return if (invocationStrategies.isEmpty()) {
                Pair(MergedInvocationStrategy(arrayOf(InvocationStrategy.NO_CACHE)), expiresAfter)
            } else {
                Pair(MergedInvocationStrategy(invocationStrategies), expiresAfter)
            }
        }

        private fun generateAmruRequestParams(requestBody: RPCRequest) = mapOf("applicationMethod" to requestBody.method)

        private fun getMaxBatchSize(method: Method): Int = with(method.getAnnotation(NoBatch::class.java)) {
            return if (this == null) maxBatchSize else 1
        }
    }


    init {
        defaultCallbackHolder = object : ICallbackHolder {
            override fun init() {}
            override fun clear() {}
            override fun hold(disposable: Disposable) {}
            override fun <I> createEmpty(type: Class<in I>): I = Wrapper.stub(type) as I
            override fun <I> wrap(source: I, interfaceType: Class<in I>) = source
        }
    }
}
