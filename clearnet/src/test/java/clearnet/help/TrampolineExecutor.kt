package clearnet.help

import java.util.*
import java.util.concurrent.Executor

class TrampolineExecutor : Executor {
    private val queue =  LinkedList<Runnable>()
    private var execution = false

    @Synchronized
    override tailrec fun execute(command: Runnable) {
        if (execution) {
            queue.add(command)
        } else {
            execution = true
            command.run()
            execution = false
            val next = queue.poll()
            if (next != null) execute(next)
        }
    }
}