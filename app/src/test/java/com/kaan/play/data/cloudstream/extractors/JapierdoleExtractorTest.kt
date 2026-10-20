package com.kaan.play.data.cloudstream.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class JapierdoleExtractorTest {

    @Test
    fun extractCr_pattern1_atob() {
        val partA = "c7489"
        val partB = "74bf9147132a"
        val b64A = JapierdoleExtractor.base64Encode(partA.toByteArray(Charsets.ISO_8859_1))
        val b64B = JapierdoleExtractor.base64Encode(partB.toByteArray(Charsets.ISO_8859_1))

        val html = """
            <html>
            <script>
            var _3a4fa = atob('$b64A');
            var _3a4fb = atob('$b64B');
            var _cr = _3a4fa + _3a4fb;
            </script>
            </html>
        """.trimIndent()

        val cr = JapierdoleExtractor.extractCr(html)
        assertNotNull(cr)
        assertEquals(partA + partB, cr)
    }

    @Test
    fun extractCr_pattern2_reverse() {
        val partA = "12345"
        val partB = "abcde"
        val revA = partA.reversed()
        val revB = partB.reversed()

        val html = """
            <html>
            <script>
            var _ff1a = '$revA';
            var _ff1b = '$revB';
            var _ff1f = function(s){return s.split('').reverse().join('')};
            var _cr = _ff1f(_ff1a) + _ff1f(_ff1b);
            </script>
            </html>
        """.trimIndent()

        val cr = JapierdoleExtractor.extractCr(html)
        assertNotNull(cr)
        assertEquals(partA + partB, cr)
    }

    @Test
    fun extractCr_pattern3_xor() {
        val target = "secret_challenge"
        val key = listOf(0x5A, 0x3F, 0x12)
        val aList = target.mapIndexed { idx, ch -> ch.code xor key[idx % key.size] }

        val aStr = aList.joinToString(",")
        val bStr = key.joinToString(",")

        val html = """
            <html>
            <script>
            var _001a = [$aStr];
            var _001b = [$bStr];
            </script>
            </html>
        """.trimIndent()

        val cr = JapierdoleExtractor.extractCr(html)
        assertNotNull(cr)
        assertEquals(target, cr)
    }

    @Test
    fun extractCr_noMatch_returnsNull() {
        val html = "<html><body>No challenge here</body></html>"
        val cr = JapierdoleExtractor.extractCr(html)
        assertNull(cr)
    }

    @Test
    fun decryptOuterPayload_correctlyDecryptsAesCbc() {
        val plainText = """{"processed_template":"videoHls: 'https://cdn.example.com/master.m3u8'"}"""
        val seed = "abc123p1p2p3p4"
        val cr = "challenge_res"
        val x = "x_salt"
        val ivHex = "00112233445566778899aabbccddeeff"

        val derivedKey = MessageDigest.getInstance("SHA-256").digest((seed + cr + x).toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = JapierdoleExtractor.hexToBytes(ivHex)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ivBytes))
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val d = JapierdoleExtractor.base64Encode(encryptedBytes)

        val decrypted = JapierdoleExtractor.decryptOuterPayload(d, ivHex, seed, cr, x)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun extractVideoUrl_findsHlsUrl() {
        val html = """
            window.playerConfig = {
                videoHls: "https://edge01.stream.com/hls/master.m3u8",
                poster: "https://poster.jpg"
            };
        """
        val url = JapierdoleExtractor.extractVideoUrl(html)
        assertEquals("https://edge01.stream.com/hls/master.m3u8", url)
    }
}
