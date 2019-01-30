package clearnet

import clearnet.interfaces.HeaderListener
import clearnet.interfaces.HeaderObserver
import clearnet.interfaces.Subscription

class SimpleHeadersObserver : HeaderObserver {
    private val headerListeners = ArrayList<HeaderSubscription>()

    override fun register(method: String, listener: HeaderListener, vararg header: String): Subscription {
        val subscription = HeaderSubscription(method, header.asList(), listener)
        synchronized(headerListeners){
            headerListeners.add(subscription)
        }

        return object : Subscription {
            override fun unsubscribe() {
                synchronized(headerListeners){
                    headerListeners.remove(subscription)
                }
            }
        }
    }

    fun propagateHeaders(method: String, headers: Map<String, String>){
        synchronized(headerListeners){
            headerListeners.filter {
                method == it.method
            }.forEach { subscription ->
                val headersList = if (!subscription.allHeaders()) {
                    headers.entries.filter { subscription.names.contains(it.key) }
                } else {
                    headers.entries
                }

                headersList.forEach {
                    subscription.listener.onNewHeader(method, it.key, it.value)
                }
            }
        }
    }

    private data class HeaderSubscription(val method: String, val names: Collection<String>, val listener: HeaderListener){
        fun allHeaders() = names.isEmpty()
    }
}