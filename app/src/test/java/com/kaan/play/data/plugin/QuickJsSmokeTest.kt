package com.kaan.play.data.plugin

import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickJsSmokeTest {
    @Test
    fun evaluatesAndCallsHostFunction() = runTest {
        var got: Any? = null
        val sum = withContext(Dispatchers.Default) {
            quickJs(Dispatchers.Default) {
                function("report") { args -> got = args[0]; null }
                evaluate<Any?>("report('hi'); 1 + 2")
            }
        }
        assertEquals("hi", got)
        assertEquals(3, (sum as Number).toInt())
    }
}
