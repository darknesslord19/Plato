package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsNextEpisodeTest {
    private val eps = listOf(
        CsEpisodeRef(1, 1, "s1e1"),
        CsEpisodeRef(1, 2, "s1e2"),
        CsEpisodeRef(2, 1, "s2e1"),
    )

    @Test fun `dataUrl eslesirse listedeki sonraki bolum`() {
        assertEquals(CsNextEpisode(1, 2, "s1e2"), CsNextEpisodeFinder.pickNext(eps, "s1e1", 1, 1))
    }

    @Test fun `sezon sonunda sonraki sezonun ilk bolumu`() {
        assertEquals(CsNextEpisode(2, 1, "s2e1"), CsNextEpisodeFinder.pickNext(eps, "s1e2", 1, 2))
    }

    @Test fun `son bolumden sonra yok`() {
        assertNull(CsNextEpisodeFinder.pickNext(eps, "s2e1", 2, 1))
    }

    @Test fun `dataUrl degismisse sezon bolum numarasiyla bulunur`() {
        assertEquals(CsNextEpisode(2, 1, "s2e1"), CsNextEpisodeFinder.pickNext(eps, "eski-adres", 1, 2))
    }

    @Test fun `numarasiz bolumlerde sira kullanilir`() {
        val plain = listOf(CsEpisodeRef(null, null, "a"), CsEpisodeRef(null, null, "b"))
        assertEquals(CsNextEpisode(1, 2, "b"), CsNextEpisodeFinder.pickNext(plain, "a", 1, 1))
    }

    @Test fun `bos liste`() {
        assertNull(CsNextEpisodeFinder.pickNext(emptyList(), "a", 1, 1))
    }
}
