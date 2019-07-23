package com.eyeem.watchadoin

import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

fun expensiveSleep(stopwatch: Stopwatch): Int = stopwatch {
    Thread.sleep(125)
    return@stopwatch 1
}

fun moreExpensiveSleep(stopwatch: Stopwatch): Int = stopwatch {
    Thread.sleep(375)
    return@stopwatch 2
}

suspend fun expensiveDelay(stopwatch: Stopwatch): Int = stopwatch {
    delay(125)
    return@stopwatch 1
}

suspend fun moreExpensiveDelay(stopwatch: Stopwatch): Int = stopwatch {
    delay(375)
    return@stopwatch 2
}

class WatchadoinTest {

    @Before
    fun warmUp() {
        runBlocking {
            launch {
                Stopwatch("warm up").invoke {
                    delay(10)
                }
            }
        }
    }

    @Test
    fun `Test 0 - API usage`() {
        val stopwatch = Stopwatch("main")

        val sum = stopwatch {
            expensiveSleep("expensiveOperation".watch) +
                    moreExpensiveSleep("moreExpensiveOperation".watch)
        }
        print(sum)

        println(stopwatch.toStringPretty())

        val svgFile = File("test0.svg")
        stopwatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")
    }

    @Test
    fun `Test 1 - Linear Sleep`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            for (i in 0 until 4) {
                "⏭️ iteration $i".watch {
                    expensiveSleep("💤".watch)

                    moreExpensiveSleep("💤 x3".watch)
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test1.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")
    }

    @Test
    fun `Test 2 - Sleep On Coroutines + GlobalScope`() = runBlocking {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            val jobs = mutableListOf<Job>()

            for (i in 0 until 4) {
                jobs += GlobalScope.async {
                    "⏭️ iteration $i".watch {
                        expensiveSleep("💤".watch)

                        moreExpensiveSleep("💤 x3".watch)
                    }
                }
            }

            jobs.forEach { it.join() }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test2.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")
    }

    @Test
    fun `Test 3 - Sleep On Coroutines + Run Blocking`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            runBlocking {
                for (i in 0 until 4) {
                    launch {
                        "⏭️ iteration $i".watch {
                            expensiveSleep("💤".watch)

                            moreExpensiveSleep("💤 x3".watch)
                        }
                    }
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test3.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")

    }

    @Test
    fun `Test 4 - Delay On Coroutines + Run Blocking`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            runBlocking {
                for (i in 0 until 4) {
                    launch {
                        "⏭️ iteration $i".watch {
                            expensiveDelay("🕰".watch)

                            moreExpensiveDelay("🕰️ x3".watch)
                        }
                    }
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test4.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")

    }

    @Test
    fun `Test 5 - Delay On Coroutines + Run Blocking + WTF`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            val initWatch =  "🚀 init".watch.apply { start() }
            runBlocking {
                initWatch.end()
                for (i in 0 until 4) {
                    launch {
                        "⏭️ iteration $i".watch {
                            expensiveDelay("🕰".watch)

                            moreExpensiveDelay("🕰️ x3".watch)
                        }
                    }
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test5.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")

    }

    @Test
    fun `Test 6 - Delay On Coroutines + Run Blocking + Failing Async`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            runBlocking {
                for (i in 0 until 4) {
                    launch {
                        "⏭️ iteration $i".watch {
                            val expensiveResult = async {  expensiveDelay("🕰".watch) }.await()
                            val moreExpensiveResult = async { moreExpensiveDelay("🕰️ x3".watch) }.await()
                            println("combined result -> ${expensiveResult + moreExpensiveResult}")
                        }
                    }
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test6.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")

    }

    @Test
    fun `Test 7 - Delay On Coroutines + Run Blocking + Proper Async`() {
        val loopWatch = Stopwatch("🔁 loop")

        loopWatch {
            runBlocking {
                for (i in 0 until 4) {
                    launch {
                        "⏭️ iteration $i".watch {
                            val expensiveResult = async {  expensiveDelay("🕰".watch) }
                            val moreExpensiveResult = async { moreExpensiveDelay("🕰️ x3".watch) }
                            println("combined result -> ${expensiveResult.await() + moreExpensiveResult.await()}")
                        }
                    }
                }
            }
        }

        println(loopWatch.toStringPretty())

        val svgFile = File("test7.svg")
        loopWatch.saveAsSvg(svgFile)
        println("SVG timeline report saved to file://${svgFile.absolutePath}")

    }
}