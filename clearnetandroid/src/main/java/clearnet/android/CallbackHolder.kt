package clearnet.android

import android.arch.lifecycle.Lifecycle.Event.ON_CREATE
import android.arch.lifecycle.Lifecycle.Event.ON_DESTROY
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import clearnet.Wrapper
import clearnet.interfaces.ICallbackHolder
import clearnet.interfaces.Subscription
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executor

open class CallbackHolder(val callbackExecutor: Executor) : LifecycleObserver, ICallbackHolder {
    override val scheduler = Schedulers.from(callbackExecutor)

    internal val callbackList: MutableList<Wrapper<*>> = ArrayList() //todo temporal visibility until tests will be moved
    internal var disposables = CompositeDisposable()
    private val subscriptions = ArrayList<Subscription>()

    @OnLifecycleEvent(ON_DESTROY)
    override fun clear() {
        callbackList.synchronized {
            forEach { it.stop() }
            clear()
        }

        disposables.dispose()

        subscriptions.synchronized {
            forEach { it.unsubscribe() }
            clear()
        }
    }

    @OnLifecycleEvent(ON_CREATE)
    override fun init() {
        if (disposables.isDisposed) disposables = CompositeDisposable()
    }

    override fun <I> createEmpty(type: Class<in I>): I = Wrapper.stub(type) as I

    override fun <I> wrap(source: I, interfaceType: Class<in I>): I {
        val wrapper = Wrapper(callbackList, callbackExecutor, source)
        callbackList.synchronized {
            add(wrapper)
        }
        return wrapper.create(interfaceType)
    }

    override fun hold(disposable: Disposable) {
        disposables.add(disposable)
    }

    fun hold(subscription: Subscription) {
        subscriptions.synchronized {
            add(subscription)
        }
    }
}

fun Disposable.hold(callbackHolder: CallbackHolder) = apply {
    callbackHolder.hold(this)
}

fun Subscription.hold(callbackHolder: CallbackHolder) = apply {
    callbackHolder.hold(this)
}

inline fun CallbackHolder.wrapBlock(crossinline block: () -> Unit): () -> Unit {
    val wrapped = wrap(Runnable { block() }, Runnable::class.java)
    return { wrapped.run() }
}

inline fun <reified T> CallbackHolder.wrap(source: T) {
    wrap(source, T::class.java)
}