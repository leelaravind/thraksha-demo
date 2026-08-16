package com.thraksha.guardian

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2: proves the targeted `<queries>` declaration actually works.
 *
 * Under Android 11+ package-visibility filtering with `targetSdk 36`, a package Thraksha
 * has not declared is invisible — `getPackageInfo` throws `NameNotFoundException` as if
 * it were not installed. These tests therefore prove the manifest change, not merely
 * that the decoys are installed.
 *
 * They are skipped (not failed) when a decoy is absent, so the suite still runs on a
 * device without the demo sandbox.
 */
@RunWith(AndroidJUnit4::class)
class PackageVisibilityInstrumentedTest {

    private val pm: PackageManager =
        InstrumentationRegistry.getInstrumentation().targetContext.packageManager

    private fun requireInstalled(pkg: String) {
        val installed = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        assumeTrue("$pkg is not installed on this device", installed)
    }

    @Test
    fun bothDecoyPackagesAreVisibleAndClassifiedAsCallerId() {
        for (pkg in DemoAppRegistry.demoPackages) {
            requireInstalled(pkg)
            val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            assertNotNull("$pkg should be visible through <queries>", info)
            assertEquals(
                "$pkg must be classified as CALLER_ID",
                AppType.CALLER_ID,
                DemoAppRegistry.typeOf(pkg),
            )
        }
    }

    @Test
    fun goodCallerDeclaresNoPhotoOrPackageListingCapability() {
        requireInstalled(DemoAppRegistry.GOOD_CALLER)
        val requested = requestedPermissions(DemoAppRegistry.GOOD_CALLER)

        assertTrue("GoodCaller should declare caller-ID permissions", requested.isNotEmpty())
        assertTrue(
            "GoodCaller must not request photo/media access",
            requested.none { it == "android.permission.READ_MEDIA_IMAGES" },
        )
        assertTrue(
            "GoodCaller must not request broad package visibility",
            requested.none { it == "android.permission.QUERY_ALL_PACKAGES" },
        )
    }

    @Test
    fun villainCallerDeclaresTheIntendedMismatchedCapabilities() {
        requireInstalled(DemoAppRegistry.VILLAIN_CALLER)
        val requested = requestedPermissions(DemoAppRegistry.VILLAIN_CALLER)

        assertTrue(
            "VillainCaller must request photo/media access",
            requested.contains("android.permission.READ_MEDIA_IMAGES"),
        )
        assertTrue(
            "VillainCaller must request broad package visibility",
            requested.contains("android.permission.QUERY_ALL_PACKAGES"),
        )
        assertTrue(
            "VillainCaller must request precise location",
            requested.contains("android.permission.ACCESS_FINE_LOCATION"),
        )
    }

    /** Both decoys must still share the same caller-ID baseline, or the demo proves nothing. */
    @Test
    fun bothDecoysShareTheSameCallerIdBaselinePermissions() {
        requireInstalled(DemoAppRegistry.GOOD_CALLER)
        requireInstalled(DemoAppRegistry.VILLAIN_CALLER)

        val baseline = setOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.INTERNET",
        )
        assertTrue(
            "GoodCaller should hold the caller-ID baseline",
            requestedPermissions(DemoAppRegistry.GOOD_CALLER).containsAll(baseline),
        )
        assertTrue(
            "VillainCaller should hold the same caller-ID baseline",
            requestedPermissions(DemoAppRegistry.VILLAIN_CALLER).containsAll(baseline),
        )
    }

    private fun requestedPermissions(pkg: String): Set<String> =
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()
}
