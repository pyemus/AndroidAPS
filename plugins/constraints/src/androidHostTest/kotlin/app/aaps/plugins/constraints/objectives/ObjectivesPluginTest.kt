package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.sharedPreferences.PreferencesImpl
import app.aaps.plugins.constraints.ConstraintsStringsValues
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective8
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import app.aaps.plugins.constraints.objectives.objectives.PlainDurationText
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.impl.sharedPreferences.SPImpl
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObjectivesPluginTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var loop: Loop
    @Mock lateinit var passwordCheck: PasswordCheck
    @Mock lateinit var profileUtilMock: ProfileUtil
    @Mock lateinit var profileFunctionMock: ProfileFunction
    @Mock lateinit var hardLimitsMock: HardLimits

    /** Real English for the objective reasons, so the sentences asserted below are what the user reads. */
    private val text = generatedTextResolver("constraints" to ConstraintsStringsValues::textOf)

    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var emulatedPreferences: Preferences

    @BeforeEach
    fun setupMock() {
        val sp = SPImpl(SharedPreferencesMock(), context)
        emulatedPreferences = PreferencesImpl(sp, { profileUtilMock }, { profileFunctionMock }, { hardLimitsMock }, persistenceLayer, config, dateUtil)

        // Real formatter: pure arithmetic over a duration, and only read for display.
        val durationText = PlainDurationText()
        val objectives = listOf(
            Objective0(emulatedPreferences, text, durationText, dateUtil, activePlugin, virtualPumpPlugin, persistenceLayer, loop, iobCobCalculator, passwordCheck),
            Objective1(emulatedPreferences, text, durationText, dateUtil),
            Objective2(emulatedPreferences, text, durationText, dateUtil),
            Objective3(emulatedPreferences, text, durationText, dateUtil),
            Objective4(emulatedPreferences, text, durationText, dateUtil, profileFunction),
            Objective5(emulatedPreferences, text, durationText, dateUtil),
            Objective6(emulatedPreferences, text, durationText, dateUtil, constraintsChecker, loop),
            Objective7(emulatedPreferences, text, durationText, dateUtil),
            Objective8(emulatedPreferences, text, durationText, dateUtil),
            Objective9(emulatedPreferences, text, durationText, dateUtil)
        )
        objectivesPlugin = ObjectivesPlugin(aapsLogger, text, emulatedPreferences, config, objectives, mock())
        runBlocking { objectivesPlugin.onStart() }
    }

    @Test fun notStartedObjectivesShouldLimitLoopInvocation() {
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Objectives: Objective 1 not started")
        assertThat(c.value()).isFalse()
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = dateUtil.now()
    }

    @Test fun notStartedObjective5ShouldForceLgs() {
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].startedOn = 1
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].accomplishedOn = 0
        val c = objectivesPlugin.isLgsForced(ConstraintObject(false, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 6 not finished")
        assertThat(c.value()).isTrue()
    }

    @Test fun notStartedObjective6ShouldLimitClosedLoop() = runTest {
        objectivesPlugin.objectives[Objectives.CLOSED_LOOP_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 7 not started")
        assertThat(c.value()).isFalse()
    }

    @Test fun notStartedObjective8ShouldLimitAutosensMode() {
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isAutosensModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 8 not started")
        assertThat(c.value()).isFalse()
    }

    @Test fun notStartedObjective10ShouldLimitSMBMode() = runTest {
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 9 not started")
        assertThat(c.value()).isFalse()
    }

    /**
     * SKIP_OBJECTIVES enabled: the plugin must contribute no restriction and no
     * reason, even with every objective unstarted. The tests above cover the
     * opposite branch -- the mocked Config returns false by default.
     */
    @Test fun skipObjectivesShouldNotLimitLoopInvocation() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isTrue()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesShouldNotForceLgs() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].startedOn = 1
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].accomplishedOn = 0
        val c = objectivesPlugin.isLgsForced(ConstraintObject(false, aapsLogger))
        assertThat(c.value()).isFalse()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesShouldNotLimitClosedLoop() = runTest {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.CLOSED_LOOP_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isTrue()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesShouldNotLimitAutosensMode() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isAutosensModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isTrue()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesShouldNotLimitSMBMode() = runTest {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isTrue()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesShouldNotLimitAutomation() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.AUTO_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isAutomationEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isTrue()
        assertThat(c.getReasons()).isEmpty()
    }

    @Test fun skipObjectivesStillLimitsConcentration() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.EXAM_OBJECTIVE].accomplishedOn = 0
        val c = objectivesPlugin.isConcentrationEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.value()).isFalse()
        assertThat(c.getReasons()).contains("Objective 3 not finished")
    }

    @Test fun skipObjectivesMarksAllAccomplishedOnStart() = runTest {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives.forEach { it.startedOn = 0; it.accomplishedOn = 0 }
        objectivesPlugin.onStart()
        assertThat(objectivesPlugin.accomplishedCount).isEqualTo(objectivesPlugin.size)
        assertThat(objectivesPlugin.objectives.all { it.isStarted && it.isAccomplished }).isTrue()
    }

    @Test fun onStartLeavesObjectivesAloneWithoutSkip() = runTest {
        objectivesPlugin.objectives[Objectives.EXAM_OBJECTIVE].accomplishedOn = 0
        objectivesPlugin.onStart()
        assertThat(objectivesPlugin.objectives[Objectives.EXAM_OBJECTIVE].isAccomplished).isFalse()
    }

    /**
     * The setup wizard gates its non-skippable Objectives screen on this, so it has
     * to report started. Stored progress itself is untouched -- the fragment reads
     * the Objective directly, not the plugin.
     */
    @Test fun skipObjectivesShouldReportFirstObjectiveStarted() {
        whenever(config.isEnabled(ExternalOptions.SKIP_OBJECTIVES)).thenReturn(true)
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = 0
        assertThat(objectivesPlugin.isStarted(Objectives.FIRST_OBJECTIVE)).isTrue()
        assertThat(objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].isStarted).isFalse()
    }
}
