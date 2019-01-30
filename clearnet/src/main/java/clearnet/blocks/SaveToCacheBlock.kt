package clearnet.blocks

import clearnet.CoreTask
import clearnet.InvocationBlockType
import clearnet.error.ConversionException
import clearnet.interfaces.ICacheProvider
import clearnet.interfaces.IInvocationBlock

class SaveToCacheBlock(
        private val cacheProvider: ICacheProvider
) : IInvocationBlock {
    override val invocationBlockType = InvocationBlockType.SAVE_TO_CACHE

    override fun onEntity(promise: CoreTask.Promise) = with(promise) {
        try {
            taskRef.getLastResult().plainResult?.let {
                cacheProvider.store(
                        taskRef.cacheKey,
                        it,
                        taskRef.postParams.expiresAfter
                )
            }
        } catch (e: ConversionException) {
            // todo log error
            e.printStackTrace()
        }
        promise.next(invocationBlockType)
    }
}