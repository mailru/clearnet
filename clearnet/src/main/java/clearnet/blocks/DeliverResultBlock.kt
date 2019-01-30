package clearnet.blocks

import clearnet.CoreTask
import clearnet.InvocationBlockType
import clearnet.interfaces.IInvocationBlock

object DeliverResultBlock : IInvocationBlock {
    override val invocationBlockType = InvocationBlockType.DELIVER_RESULT

    override fun onEntity(promise: CoreTask.Promise) = with(promise) {
        taskRef.deliver(taskRef.getLastResult())
        super.onEntity(promise)
    }
}