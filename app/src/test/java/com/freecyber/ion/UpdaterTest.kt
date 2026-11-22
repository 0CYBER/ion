package com.freecyber.ion

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdaterTest {
    private val json = """
        [
          {"tag_name":"build-190","name":"Ion 0.9.3-ai · сборка 190","prerelease":true,"draft":false,
           "html_url":"https://github.com/0CYBER/ion/releases/tag/build-190","body":"- Newest",
           "assets":[{"name":"ion-190.apk","size":2300000,"browser_download_url":"https://github.com/0CYBER/ion/releases/download/build-190/ion-190.apk"}]},
          {"tag_name":"v0.9.3-ai-b180","name":"","prerelease":false,"draft":false,"html_url":"","body":"",
           "assets":[{"name":"notes.txt","size":1,"browser_download_url":"x"},{"name":"ion-180.apk","size":2200000,"browser_download_url":"https://e/ion-180.apk"}]},
          {"tag_name":"draft","name":"d","prerelease":false,"draft":true,"assets":[{"name":"ion-999.apk","size":1,"browser_download_url":"y"}]},
          {"tag_name":"old","name":"old","prerelease":false,"draft":false,"assets":[{"name":"app-release.apk","size":1,"browser_download_url":"z"}]}
        ]
    """.trimIndent()

    @Test fun parsesApkReleasesAndSkipsDraftsAndUnnamedAssets() {
        val releases = Updater.parseReleases(json)
        assertEquals(listOf(190, 180), releases.map { it.build })
        val pre = releases[0]
        assertTrue(pre.prerelease); assertEquals(2300000L, pre.apkSize); assertEquals("- Newest", pre.notes)
        assertTrue(pre.apkUrl.endsWith("/ion-190.apk"))
        val stable = releases[1]
        assertEquals("v0.9.3-ai-b180", stable.title)
        assertEquals(Updates.RELEASES_PAGE, stable.pageUrl)
        assertEquals(180, Updates.pick(releases, UpdateChannel.STABLE, 175)?.build)
        assertEquals(190, Updates.pick(releases, UpdateChannel.PRERELEASE, 175)?.build)
    }
}
