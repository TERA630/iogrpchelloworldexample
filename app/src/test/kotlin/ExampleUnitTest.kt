import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun coroutineTest() {
        val channel = Channel<String>()
        val resultList = mutableListOf<String>()

        runBlocking {
            repeat(5) {
                channel.send("Go")
            }
        }

        GlobalScope.launch {
            repeat(5) {
                val reader = channel.receive()
                println(reader)
                resultList.add(reader)
            }
        }
        assertEquals(mutableListOf("Go", "Go", "Go", "Go", "Go"), resultList)

    }
}