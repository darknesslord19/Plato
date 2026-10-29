package com.kaan.play.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthRulesTest {
    @Test
    fun statusFromCodeAndTime() {
        assertEquals(HealthStatus.Ok, HealthRules.status(200, 300))
        assertEquals(HealthStatus.Ok, HealthRules.status(302, 300))
        assertEquals(HealthStatus.Slow, HealthRules.status(200, HealthRules.SLOW_MS + 1))
        assertEquals(HealthStatus.Unreachable, HealthRules.status(404, 100))
        assertEquals(HealthStatus.Unreachable, HealthRules.status(503, 100))
        assertEquals(HealthStatus.Unreachable, HealthRules.status(null, 100))
    }

    @Test
    fun problemsFirst() {
        val items = listOf(
            HealthItem("a", "Bravo", HealthKind.Addon, HealthStatus.Ok),
            HealthItem("b", "Alfa", HealthKind.CsPlugin, HealthStatus.LoadFailed),
            HealthItem("c", "Charlie", HealthKind.CsRepo, HealthStatus.Slow),
            HealthItem("d", "Delta", HealthKind.NuvioRepo, HealthStatus.Unreachable),
        )
        assertEquals(listOf("Alfa", "Delta", "Charlie", "Bravo"), HealthRules.sorted(items).map { it.name })
        assertEquals(2, HealthRules.problemCount(items))
    }
}
