package com.kaan.play.ui.home

import com.kaan.play.R
import com.kaan.play.data.home.HomeCatalog
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeSectionTitlesTest {
    @Test
    fun everyPresetAndSpecialHasItsOwnTitle() {
        (HomeCatalog.SPECIALS + HomeCatalog.PRESETS.map { it.id }).forEach {
            assertNotEquals(it, R.string.home_cat_unknown, homeSectionTitleRes(it))
        }
    }
}
