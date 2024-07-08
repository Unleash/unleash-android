package io.getunleash.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.Executors

class CoroutineTest {
    private val testDispatcher = StandardTestDispatcher()

    val customIODispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Custom-IO-Thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    @Test
    fun testCoroutineScopes() {
        runBlocking {
            println("Running on: ${Thread.currentThread().name}") // Prints main thread

            launch {
                println("Running on: ${Thread.currentThread().name}") // Prints main thread

                withContext(Dispatchers.IO) {
                    println("Switched to: ${Thread.currentThread().name}") // Prints a background thread
                    // Simulate IO work
                    delay(1000)
                }

                launch {
                    println("[inner] Running on: ${Thread.currentThread().name}") // Prints a background thread

                    withContext(Dispatchers.IO) {
                        println("[inner] Switched 3 to: ${Thread.currentThread().name}") // Prints a background thread
                        // Simulate IO work
                        delay(100)
                    }

                    println("[inner] Back to: ${Thread.currentThread().name}") // Back to the main thread
                }

                withContext(Dispatchers.IO) {
                    println("Switched 2 to: ${Thread.currentThread().name}") // Prints a background thread
                    // Simulate IO work
                    delay(1000)
                }

                println("Back to: ${Thread.currentThread().name}") // Back to the main thread
            }
            println("End of runBlocking: ${Thread.currentThread().name}") // Back to the main thread
        }
    }

    @Test
    fun supervisorTest()  {
        runBlocking {
            val handler = CoroutineExceptionHandler { _, exception ->
                println("Caught exception at supervisor level: ${exception.message}")
            }
            // Create a supervisor scope with SupervisorJob
            val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

            // Launch child coroutines
            supervisorScope.launch {
                println("Child 1 starts on ${Thread.currentThread().name}")
                delay(1000)
                println("Child 1 completes on ${Thread.currentThread().name}")
            }

            supervisorScope.launch {
                println("Child 2 starts on ${Thread.currentThread().name}")
                delay(500)
                throw Exception("Child 2 failed!")
            }

            supervisorScope.launch {
                println("Child 3 starts on ${Thread.currentThread().name}")
                withContext(customIODispatcher) {
                    println("Child 3 switched to ${Thread.currentThread().name} for I/O work")
                    delay(2000)
                    println("Child 3 completes on ${Thread.currentThread().name} after I/O work")
                }
                println("Child 3 completes on ${Thread.currentThread().name}")
            }

            // Give coroutines time to complete
            delay(3000)

            // Cancel the supervisor scope to clean up resources
            supervisorScope.cancel()
        }
    }
}
