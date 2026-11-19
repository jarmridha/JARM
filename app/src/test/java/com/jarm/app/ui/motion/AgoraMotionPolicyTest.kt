package com.jarm.app.ui.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JARMMotionPolicyTest {
    @Test
    fun fullMotionRemainsAvailableWhenNeitherPreferenceRequestsReduction() {
        val policy = resolveJARMMotionPolicy(
            appReduceMotion = false,
            systemAnimationsDisabled = false,
        )

        assertFalse(policy.reduceMotion)
        assertFalse(policy.systemAnimationsDisabled)
        assertTrue(policy.allowContinuousMotion)
        assertTrue(policy.allowSpatialTransitions)
        assertTrue(policy.allowProgrammaticScrollMotion)
    }

    @Test
    fun appPreferenceDisablesMotionSensitiveCapabilities() {
        val policy = resolveJARMMotionPolicy(
            appReduceMotion = true,
            systemAnimationsDisabled = false,
        )

        assertReduced(policy)
        assertFalse(policy.systemAnimationsDisabled)
    }

    @Test
    fun systemRemoveAnimationsAlsoDisablesMotionSensitiveCapabilities() {
        val policy = resolveJARMMotionPolicy(
            appReduceMotion = false,
            systemAnimationsDisabled = true,
        )

        assertReduced(policy)
        assertTrue(policy.systemAnimationsDisabled)
    }

    private fun assertReduced(policy: JARMMotionPolicy) {
        assertTrue(policy.reduceMotion)
        assertFalse(policy.allowContinuousMotion)
        assertFalse(policy.allowSpatialTransitions)
        assertFalse(policy.allowProgrammaticScrollMotion)
    }
}
