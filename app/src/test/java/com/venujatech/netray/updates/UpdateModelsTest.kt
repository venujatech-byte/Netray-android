package com.venujatech.netray.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelsTest {
    @Test
    fun semverComparesNumericallyAndSupportsPrefixBuildAndPrerelease() {
        assertTrue(VersionSemver.isNewer("v1.10.0", "1.9.0"))
        assertTrue(VersionSemver.isNewer("android-v2.0.0", "1.99.99"))
        assertTrue(VersionSemver.isNewer("1.0.0", "1.0.0-rc.9"))
        assertTrue(VersionSemver.isNewer("1.0.0-rc.10", "1.0.0-rc.2"))
        assertEquals(0, VersionSemver.compare("v1.2.3+ci.12", "1.2.3+ci.14"))
        assertFalse(VersionSemver.isNewer("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun picksNewestStableAndroidAssetUsingAbiPreferenceAndUniversalFallback() {
        val json = """
            [
              {"tag_name":"android-v1.3.0","draft":false,"prerelease":false,"assets":[{"name":"linux.tar.gz","browser_download_url":"https://github.com/example/linux"}]},
              {"tag_name":"android-v1.2.1","name":"Android 1.2.1","body":"Notes","published_at":"2026-01-02T00:00:00Z","draft":false,"prerelease":false,"assets":[
                {"name":"Netray-Android-v1.2.1-universal.apk","browser_download_url":"https://github.com/example/universal.apk","size":80},
                {"name":"Netray-Android-v1.2.1-arm64-v8a.apk","browser_download_url":"https://github.com/example/arm64.apk","size":40,"digest":"sha256:abc"},
                {"name":"SHA256SUMS.txt","browser_download_url":"https://github.com/example/SHA256SUMS.txt","size":10}
              ]},
              {"tag_name":"android-v1.4.0-beta.1","draft":false,"prerelease":true,"assets":[{"name":"Netray-Android-v1.4.0-beta.1-universal.apk","browser_download_url":"https://github.com/example/beta.apk"}]}
            ]
        """.trimIndent()
        val selected = AndroidReleaseParser.newestCompatibleRelease(json, "1.2.0", listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("1.2.1", selected?.version)
        assertEquals("Netray-Android-v1.2.1-arm64-v8a.apk", selected?.assetName)
        assertEquals("sha256:abc", selected?.digest)

        val fallback = AndroidReleaseParser.newestCompatibleRelease(json, "1.2.0", listOf("x86"))
        assertEquals("Netray-Android-v1.2.1-universal.apk", fallback?.assetName)
    }

    @Test
    fun ignoresDraftPrereleaseAndLinuxOnlyEntries() {
        val json = """
            [
              {"tag_name":"android-v2.0.0","draft":true,"prerelease":false,"assets":[{"name":"Netray-Android-v2.0.0-universal.apk","browser_download_url":"https://github.com/u/a.apk"}]},
              {"tag_name":"android-v2.1.0","draft":false,"prerelease":true,"assets":[{"name":"Netray-Android-v2.1.0-universal.apk","browser_download_url":"https://github.com/u/b.apk"}]},
              {"tag_name":"v2.2.0","draft":false,"prerelease":false,"assets":[{"name":"netray.tar.gz","browser_download_url":"https://github.com/u/netray.tar.gz"}]}
            ]
        """.trimIndent()
        assertNull(AndroidReleaseParser.newestCompatibleRelease(json, "1.0.0", listOf("arm64-v8a")))
    }

    @Test
    fun emptyBodyIsAllowedAndAabOnlyReleaseDoesNotCountAsAndroidApk() {
        val json = """[{"tag_name":"android-v1.1.0","draft":false,"prerelease":false,"body":"","assets":[{"name":"Netray.aab","browser_download_url":"https://github.com/u/app.aab"}]}]"""
        assertFalse(AndroidReleaseParser.hasCompatibleAndroidAsset(json, listOf("arm64-v8a")))
        assertNull(AndroidReleaseParser.newestCompatibleRelease(json, "1.0.0", listOf("arm64-v8a")))
    }
}
