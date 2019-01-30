package clearnet

import clearnet.error.UnknownExternalException
import clearnet.interfaces.*
import clearnet.interfaces.IInvocationBlock.QueueAlgorithm.IMMEDIATE
import clearnet.interfaces.IInvocationBlock.QueueAlgorithm.TIME_THRESHOLD
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Default realization of [IConverterExecutor] with the validation models feature
 */
class Core(
        private val ioExecutor: Executor,
        private val worker: Scheduler = Schedulers.single(),
        private val timeTracker: TaskTimeTracker? = null,
        vararg blocks: IInvocationBlock
) : IConverterExecutor, ICallbackStorage {
    private val flow: Map<InvocationBlockType, Subject<CoreTask>>
    private val taskStorage: MutableList<CoreTask> = CopyOnWriteArrayList()
    private val ioScheduler = Schedulers.from(ioExecutor)

    // todo support nulls
    private val collector = PublishSubject.create<Pair<CoreTask, CoreTask.Result>>().toSerialized()

    init {
        flow = blocks.associate { block ->
            val subject = PublishSubject.create<CoreTask>().toSerialized()

            when (block.queueAlgorithm) {
                IMMEDIATE -> subject.subscribeImmediate(block)
                TIME_THRESHOLD -> subject.subscribeWithTimeThreshold(block)
            }

            block.invocationBlockType to subject
        }
    }


    override fun executePost(postParams: PostParams) {
        Observable.just(postParams.bindable).subscribeOn(worker).flatMap { bindable ->
            if (bindable) Observable.fromIterable(taskStorage)
            else Observable.empty()
        }.filter { taskItem ->
            taskItem.respond(postParams.requestTypeIdentifier, postParams.cacheKey)
        }.sorted(CoreTask.ResultsCountComparator).switchIfEmpty {
            val task = CoreTask(postParams)
            taskStorage += task
            placeToQueue(task, InvocationBlockType.INITIAL)
            task.observe().subscribe { collector.onNext(task to it) }
            it.onNext(task)
        }.take(1).flatMap {
            it.observe()
        }.flatMap {
            if (it is CoreTask.ErrorResult) Observable.error(it.error)
            else Observable.just((it as CoreTask.SuccessResult).result)
        }.observeOn(ioScheduler).subscribe(postParams.subject)
    }


    override fun subscribe(method: String, callback: RequestCallback<*>, once: Boolean): Subscription {
        val disposable = collector.filter { (task, _) -> task.respond(method, null) }
                .compose { if (once) it.take(1) else it }
                .map { it.second }.subscribe {
                    if (it is CoreTask.SuccessResult) {
                        (callback as RequestCallback<Any?>).onSuccess(it.result)
                    } else if (it is CoreTask.ErrorResult) {
                        callback.onFailure(it.error)
                    }
                }

        return object : Subscription {
            override fun unsubscribe() = disposable.dispose()
        }
    }

    override fun <T> observe(method: String): Observable<T> {
        return collector.filter { (task, _) -> task.respond(method, null) }
                .map { it.second }
                .filter { it is CoreTask.SuccessResult }
                .map { (it as CoreTask.SuccessResult).result as T }
    }


    private fun placeToQueue(task: CoreTask, index: InvocationBlockType) {
        flow[index]!!.onNext(task)
    }

    private fun placeToQueues(from: InvocationBlockType?, task: CoreTask, indexes: Array<InvocationBlockType>) {
        task.move(from, indexes)
        indexes.forEach { placeToQueue(task, it) }
        if (task.isFinished()) {
            taskStorage.remove(task)
            timeTracker?.onTaskFinished(
                    task.postParams.invocationStrategy,
                    task.postParams.requestTypeIdentifier,
                    System.currentTimeMillis() - task.startTime
            )
        }
    }


    private fun handleTaskResult(block: IInvocationBlock, task: CoreTask, result: CoreTask.Result) {
        placeToQueues(block.invocationBlockType, task, result.nextIndexes)
    }

    private fun Observable<CoreTask>.subscribeImmediate(block: IInvocationBlock) {
        this.observeOn(worker).subscribe { task ->
            val promise = task.promise().apply {
                observe().observeOn(Schedulers.trampoline()).subscribe { result ->
                    handleTaskResult(block, task, result)
                }
            }

            // todo need test this
            ioExecutor.execute {
                try{
                    block.onEntity(promise)
                }catch (e: Throwable){
                    promise.setError(UnknownExternalException(e.message), block.invocationBlockType)
                }
            }
        }
    }

    private fun Observable<CoreTask>.subscribeWithTimeThreshold(block: IInvocationBlock) {
        this.buffer(block.queueTimeThreshold, TimeUnit.MILLISECONDS, worker).filter {
            !it.isEmpty()
        }.subscribe { taskList ->
            val promises = taskList.map { task ->
                task.promise().apply {
                    observe().observeOn(Schedulers.trampoline()).subscribe { result ->
                        handleTaskResult(block, task, result)
                    }
                }
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onQueueConsumed(promises)
                }catch (e: Throwable){
                    promises.forEach {
                        it.setError(UnknownExternalException(e.message), block.invocationBlockType)
                    }
                }
            }
        }
    }
}
