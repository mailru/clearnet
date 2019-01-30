package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.interfaces.IInvocationBlock

object InitialBlock : IInvocationBlock {
    override val invocationBlockType = InvocationBlockType.INITIAL
}