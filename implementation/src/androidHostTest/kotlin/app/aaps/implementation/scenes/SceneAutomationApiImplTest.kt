package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * The two "is there a scene" questions and how they differ once a scene expires but its banner is
 * still up — the state that used to block every automation gated on "no scene running" (#5059).
 */
class SceneAutomationApiImplTest : TestBase() {

    @Mock lateinit var sceneRepository: SceneRepository
    @Mock lateinit var sceneExecutor: SceneExecutor
    @Mock lateinit var activeSceneManager: ActiveSceneManager
    @Mock lateinit var sceneChainTargetResolver: SceneChainTargetResolver
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var rh: ResourceHelper

    private fun createSut() = SceneAutomationApiImpl(
        sceneRepository, sceneExecutor, activeSceneManager, sceneChainTargetResolver, notificationManager, rh
    )

    private fun givenScene(active: Boolean, expired: Boolean) {
        whenever(activeSceneManager.isActive()).thenReturn(active)
        whenever(activeSceneManager.isExpired()).thenReturn(expired)
    }

    @Test
    fun `a running scene is active and stoppable`() {
        givenScene(active = true, expired = false)

        assertThat(createSut().isAnySceneActive()).isTrue()
        assertThat(createSut().hasSceneToStop()).isTrue()
    }

    @Test
    fun `an expired scene is no longer active but is still stoppable`() {
        // Its effects were reverted by onExpiry; only the "ended" banner is left. Automation gated on
        // "no scene running" must be free to run, while the user (or the watch) can still dismiss it.
        givenScene(active = true, expired = true)

        assertThat(createSut().isAnySceneActive()).isFalse()
        assertThat(createSut().hasSceneToStop()).isTrue()
    }

    @Test
    fun `with no scene at all both answers are no`() {
        givenScene(active = false, expired = false)

        assertThat(createSut().isAnySceneActive()).isFalse()
        assertThat(createSut().hasSceneToStop()).isFalse()
    }

    private fun state(id: String, lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE) = ActiveSceneState(
        scene = Scene(id = id, name = id),
        activatedAt = 1000L,
        durationMs = 3_600_000L,
        lifecycle = lifecycle
    )

    @Test
    fun `activeFlow emits again when the scene expires or another scene takes over`() = runTest {
        // The watch re-reads the state on every emission: expiry turns the tile's End into Dismiss,
        // and a chain that swaps one scene for another in one step still has to reach the tile.
        // Repeating the same state must not, or the tile would redraw for nothing.
        val states = MutableStateFlow<ActiveSceneState?>(null)
        whenever(activeSceneManager.activeSceneState).thenReturn(states)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { createSut().activeFlow.collect { seen += it } }
        runCurrent()

        // Stepped one at a time: a StateFlow keeps only its latest value, so the collector has to
        // see each change before the next one lands, as it does in the app
        listOf(state("s1"), state("s1"), state("s1", lifecycle = SceneLifecycle.EXPIRED), state("s2"), null).forEach {
            states.value = it
            runCurrent()
        }

        assertThat(seen).containsExactly(false, true, true, true, false).inOrder()
    }
}
