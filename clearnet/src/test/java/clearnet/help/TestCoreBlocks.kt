package clearnet.help

import clearnet.CoreTask
import clearnet.InvocationBlockType
import clearnet.InvocationBlockType.*
import clearnet.blocks.*
import clearnet.interfaces.*

open class TestCoreBlocks(
        private val converter: ISerializer = GsonTestSerializer(),
        private val validator: IBodyValidator = BodyValidatorStub,
        private val cacheProvider: ICacheProvider = CacheProviderStub) {

    private val getFromNetBlock by lazy { GetFromNetBlock(validator, converter) }
    private val getFromCacheBlock by lazy { GetFromCacheBlock(cacheProvider, converter) }
    private val saveToCacheBlock by lazy { SaveToCacheBlock(cacheProvider) }
    private val errorsResolverBlock by lazy {
        createErrorsResolverBlock()
    }

    private val checkAuthTokenBlock by lazy {
        createAuthTokenBlock()
    }

    val getFromNetTimeThreshold = getFromNetBlock.queueTimeThreshold

    fun getAll(): Array<IInvocationBlock> {
        return InvocationBlockType.values().map {
            getBlock(it)
        }.toTypedArray()
    }

    fun getHeadersObserver() = getFromNetBlock.getHeadersObserver()

    protected open fun createErrorsResolverBlock(): IInvocationBlock = EmptyErrorsResolverBlock

    protected open fun createAuthTokenBlock(): IInvocationBlock = EmptyAuthTokenBlock

    private fun getBlock(type: InvocationBlockType) = when (type) {
        INITIAL -> InitialBlock
        GET_FROM_CACHE -> getFromCacheBlock
        GET_FROM_NET -> getFromNetBlock
        SAVE_TO_CACHE -> saveToCacheBlock
        DELIVER_RESULT -> DeliverResultBlock
        DELIVER_ERROR -> DeliverErrorBlock
        RESOLVE_ERROR -> errorsResolverBlock
        CHECK_AUTH_TOKEN -> checkAuthTokenBlock
    }

    object EmptyErrorsResolverBlock : IInvocationBlock {
        override val invocationBlockType: InvocationBlockType
            get() = RESOLVE_ERROR

        override fun onEntity(promise: CoreTask.Promise) {
            promise.setNextIndex(DELIVER_ERROR)
        }
    }

    object EmptyAuthTokenBlock : IInvocationBlock {
        override val invocationBlockType = CHECK_AUTH_TOKEN
    }
}
