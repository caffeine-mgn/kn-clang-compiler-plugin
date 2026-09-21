package pw.binom.kotlin.clang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun compareTest() {
        assertEquals(Version("1.0.0"), Version("1.0.0"))
        assertEquals(0, Version("1.0").compareTo(Version("1.0.0")))
        assertEquals(0, Version("1.0.0").compareTo(Version("1.0.0")))
        assertEquals(0, Version("1.0.0").compareTo(Version("1.0")))
        assertTrue(Version("1.1.0") > Version("1.0.0"))
        assertTrue(Version("1.0.1") > Version("1.0.0"))
        assertTrue(Version("2.0.0") > Version("1.0.0"))
        assertTrue(Version("2.0") > Version("1.0.0"))
        assertTrue(Version("2.0.0") > Version("1.0"))
        assertTrue(Version("1.0.1") > Version("1.0"))
    }
}