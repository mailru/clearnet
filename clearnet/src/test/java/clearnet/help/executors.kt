package clearnet.help

import java.util.concurrent.Executor

object ImmediateExecutor : Executor {
    override fun execute(task: Runnable) {
        task.run()
    }
}

object MultiThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        Thread(command).start()
    }
}