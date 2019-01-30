package clearnet.help

import io.reactivex.schedulers.TestScheduler
import org.junit.Before
import java.util.concurrent.TimeUnit

abstract class CoreBlocksTest {
    protected lateinit var coreBlocks: TestCoreBlocks
    protected lateinit var testScheduler: TestScheduler
    protected var timeT = 0L

    @Before
    fun baseSetUp() {
        coreBlocks = TestCoreBlocks()
        timeT = coreBlocks.getFromNetTimeThreshold
        testScheduler = TestScheduler()
    }

    protected fun forwardScheduler(){
        testScheduler.advanceTimeBy(timeT, TimeUnit.MILLISECONDS)
    }
}