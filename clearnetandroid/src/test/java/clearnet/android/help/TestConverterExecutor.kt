package clearnet.android.help

import clearnet.interfaces.IConverterExecutor
import clearnet.model.PostParams

class TestConverterExecutor : IConverterExecutor {
    var lastParams: PostParams? = null

    override fun executePost(postParams: PostParams) {
        lastParams = postParams
    }
}