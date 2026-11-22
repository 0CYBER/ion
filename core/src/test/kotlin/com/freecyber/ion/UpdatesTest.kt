package com.freecyber.ion

import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    private fun release(build: Int, pre: Boolean) =
        ReleaseInfo("t$build", "Ion $build", build, pre, "https://x/ion-$build.apk", 1, "", "https://x")

    private val releases = listOf(release(180, false), release(185, true), release(190, true), release(170, false))

    @Test fun buildIsReadFromTheAssetNameOnly() {
        assertEquals(185, Updates.buildOf("ion-185.apk"))
        assertNull(Updates.buildOf("app-release.apk"))
        assertNull(Updates.buildOf("ion-185.apk.sha256"))
    }

    @Test fun stableIgnoresPrereleasesAndPrereleaseTakesTheNewest() {
        assertEquals(180, Updates.pick(releases, UpdateChannel.STABLE, 175)?.build)
        assertEquals(190, Updates.pick(releases, UpdateChannel.PRERELEASE, 175)?.build)
    }

    @Test fun nothingOfferedWhenCurrentIsNewest() {
        assertNull(Updates.pick(releases, UpdateChannel.STABLE, 180))
        assertNull(Updates.pick(releases, UpdateChannel.PRERELEASE, 190))
    }

    @Test fun skippedBuildIsNotOfferedButNewerOnesAre() {
        assertNull(Updates.pick(releases, UpdateChannel.PRERELEASE, 175, skippedBuild = 190))
        assertEquals(190, Updates.pick(releases, UpdateChannel.PRERELEASE, 175, skippedBuild = 185)?.build)
    }

    @Test fun longNotesAreCutAtALineBreak() {
        val notes = (1..200).joinToString("\n") { "- change $it" }
        val short = Updates.shortNotes(notes, 100)
        assertTrue(short.length < 110); assertTrue(short.endsWith("\n…"))
        assertEquals("- one", Updates.shortNotes("- one\r\n"))
    }
}
