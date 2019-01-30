package clearnet.android.help

import java.util.concurrent.Executor

object ImmediateExecutor : Executor {
    override fun execute(task: Runnable) {
        task.run()
    }
}