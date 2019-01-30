package clearnet.android

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteCacheProviderTest {
    lateinit var cacheProvider: SqliteCacheProvider

    @Before
    fun setUp(){
        cacheProvider = SqliteCacheProvider(InstrumentationRegistry.getTargetContext(), "test_cache" + System.currentTimeMillis(), 1)
    }

    @After
    fun tearDown(){
        cacheProvider.clear()
    }

    @Test
    fun storeAndGetTest() {
        cacheProvider.store("1", "val1", 1000000)
        cacheProvider.store("2", "val2", 1000000)
        assertEquals("val1", cacheProvider.obtain("1"))
        assertEquals("val2", cacheProvider.obtain("2"))
        assertNull(cacheProvider.obtain("3"))
    }

    @Test
    fun replaceTest(){
        cacheProvider.store("1", "val1", 1000000)
        cacheProvider.store("1", "val2", 1000000)
        assertEquals("val2", cacheProvider.obtain("1"))
    }

    @Test
    fun expirationTest(){
        cacheProvider.store("1", "val1", 10)
        Thread.sleep(15)
        assertNull(cacheProvider.obtain("1"))
    }

    @Test
    fun clearTest(){
        cacheProvider.store("1", "val1", 1000000)
        cacheProvider.clear()
        assertNull(cacheProvider.obtain("1"))
    }
}