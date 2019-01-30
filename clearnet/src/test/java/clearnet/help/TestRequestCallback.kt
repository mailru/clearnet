package clearnet.help

import clearnet.error.ClearNetworkException
import clearnet.interfaces.RequestCallback
import java.util.*
import kotlin.collections.ArrayList

class TestRequestCallback<T>: RequestCallback<T> {
    val successes = Collections.synchronizedList(ArrayList<T>())
    val errors = Collections.synchronizedList(ArrayList<ClearNetworkException>())

    override fun onFailure(exception: ClearNetworkException) {
        errors += exception
    }

    override fun onSuccess(response: T) {
        successes += response
    }
}