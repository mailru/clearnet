package clearnet.utils

import clearnet.interfaces.ICallbackStorage
import clearnet.interfaces.RequestCallback
import clearnet.interfaces.Subscription
import java.util.*


class Subscriber<T>(
        private val callbackStorage: ICallbackStorage,
        private val method: String
) {
    private val others: MutableList<Subscriber<T>> = LinkedList()

    fun subscribe(callback: RequestCallback<T>) = subscribe(callback, false)

    fun subscribeOnce(callback: RequestCallback<T>): Subscription = subscribe(callback, true)

    fun mergeWith(another: Subscriber<T>) = apply {
        others += another
        others += another.others
    }

    operator fun plusAssign(another: Subscriber<T>) {
        mergeWith(another)
    }

    private fun subscribe(callback: RequestCallback<T>, flag: Boolean): Subscription {
        val subscription = CompoundSubscription()
        subscription += callbackStorage.subscribe(method, callback, flag)
        others.forEach {
            subscription += callbackStorage.subscribe(it.method, callback, flag)
        }
        return subscription
    }
}

class CompoundSubscription : Subscription {
    val subscriptions = LinkedList<Subscription>()

    @Synchronized
    override fun unsubscribe() {
        subscriptions.forEach {
            it.unsubscribe()
        }

        subscriptions.clear()
    }

    @Synchronized
    operator fun plusAssign(subscription: Subscription) {
        subscriptions += subscription
    }
}

