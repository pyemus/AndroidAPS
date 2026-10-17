package app.aaps.wear.tile

import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.tile.source.ActionSource
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the shared tile layout engine [TileBase] via the concrete [ActionsTileService]: onTileRequest across
 * every [WearControl] state and the 0–4 action grid (and that a longer list is cut to four), the
 * freshness/resource-version wiring, and onResourcesRequest image mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class TileBaseTest {

    private val prefs = mock<Preferences>()
    private val source = mock<ActionSource>()

    private fun service(): ActionsTileService =
        Robolectric.buildService(ActionsTileService::class.java).get().also {
            it.preferences = prefs
            it.aapsLogger = AAPSLoggerTest()
            it.actionSource = source
        }

    private fun action(text: String? = "Bolus", sub: String? = "1.0U") =
        Action(buttonText = text, buttonTextSub = sub, activityClass = "app.aaps.wear.Foo", iconRes = 0, action = null, message = null)

    private fun request(round: Boolean = true, widthDp: Int = 220, heightDp: Int = 220): RequestBuilders.TileRequest {
        // Only ROUND is a named constant here; leaving the shape unset (undefined) is != ROUND, so it drives the
        // non-round `else` branch of circleDiameter().
        val deviceParams = DeviceParameters.Builder()
            .setScreenWidthDp(widthDp)
            .setScreenHeightDp(heightDp)
            .apply { if (round) setScreenShape(SCREEN_SHAPE_ROUND) }
            .build()
        return RequestBuilders.TileRequest.Builder().setDeviceConfiguration(deviceParams).build()
    }

    private fun enabled() {
        whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(true)
        whenever(prefs.get(BooleanKey.WearControl)).thenReturn(true)
    }

    private fun disabled() {
        whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(false)
        whenever(prefs.get(BooleanKey.WearControl)).thenReturn(false)
    }

    private fun noData() = whenever(prefs.getIfExists(BooleanKey.WearControl)).thenReturn(null)

    // onTileRequest / onResourcesRequest are framework-only `protected` entry points; invoke them via reflection
    // so the full real code path runs without widening production visibility for a test.
    @Suppress("UNCHECKED_CAST")
    private fun TileBase.tileRequest(req: RequestBuilders.TileRequest): TileBuilders.Tile {
        val m = TileBase::class.java.getDeclaredMethod("onTileRequest", RequestBuilders.TileRequest::class.java).apply { isAccessible = true }
        return (m.invoke(this, req) as ListenableFuture<TileBuilders.Tile>).get()
    }

    @Suppress("UNCHECKED_CAST")
    private fun TileBase.resourcesRequest(req: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        val m = TileBase::class.java.getDeclaredMethod("onResourcesRequest", RequestBuilders.ResourcesRequest::class.java).apply { isAccessible = true }
        return (m.invoke(this, req) as ListenableFuture<ResourceBuilders.Resources>).get()
    }

    // The Material 3 primary layout wraps the content in boxes and columns of its own, and the title slot
    // adds a Text. Walk the whole tree and look for what the tile is meant to show: a message Text, or one
    // icon Image per action button.
    private fun flatten(element: LayoutElement?): List<LayoutElement> = when (element) {
        null      -> emptyList()
        is Box    -> listOf(element) + element.contents.flatMap(::flatten)
        is Column -> listOf(element) + element.contents.flatMap(::flatten)
        is Row    -> listOf(element) + element.contents.flatMap(::flatten)
        else      -> listOf(element)
    }

    private fun elementsOf(svc: ActionsTileService, req: RequestBuilders.TileRequest = request()): List<LayoutElement> =
        flatten(svc.tileRequest(req).tileTimeline?.timelineEntries?.first()?.layout?.root)

    private fun textsOf(elements: List<LayoutElement>): List<String> = elements.filterIsInstance<Text>().mapNotNull { it.text?.value }

    private fun iconCount(elements: List<LayoutElement>): Int = elements.count { it is Image }

    private fun assertMessage(svc: ActionsTileService, message: String) {
        val elements = elementsOf(svc)
        assertThat(textsOf(elements)).contains(message)
        assertThat(iconCount(elements)).isEqualTo(0)
    }

    // ---- WearControl states: each renders an explanatory Text, never buttons ----

    @Test fun `no data shows a text message`() {
        noData()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        val svc = service()
        assertMessage(svc, svc.getString(R.string.wear_control_no_data))
    }

    @Test fun `disabled shows a text message`() {
        disabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        val svc = service()
        assertMessage(svc, svc.getString(R.string.wear_control_not_enabled))
    }

    @Test fun `enabled with no actions shows the no-config text`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(emptyList())
        whenever(source.getValidFor()).thenReturn(null)
        val svc = service()
        assertMessage(svc, svc.getString(R.string.tile_no_config))
    }

    // ---- grid arrangements: 1..4 actions all build one button per action ----

    @Test fun `enabled with 1 to 4 actions builds one button per action`() {
        enabled()
        whenever(source.getValidFor()).thenReturn(null)
        for (n in 1..4) {
            whenever(source.getSelectedActions()).thenReturn((1..n).map { action(text = "A$it") })
            val elements = elementsOf(service())
            assertThat(iconCount(elements)).isEqualTo(n)
            assertThat(textsOf(elements)).containsAtLeastElementsIn((1..n).map { "A$it" })
        }
    }

    /** Five QuickWizard entries valid at the same time matched no arrangement, and the tile came out blank. */
    @Test fun `enabled with more than 4 actions draws the first four`() {
        enabled()
        whenever(source.getValidFor()).thenReturn(null)
        whenever(source.getSelectedActions()).thenReturn((1..6).map { action(text = "A$it") })
        val elements = elementsOf(service())
        assertThat(iconCount(elements)).isEqualTo(4)
        assertThat(textsOf(elements)).containsAtLeast("A1", "A2", "A3", "A4")
        assertThat(textsOf(elements)).containsNoneOf("A5", "A6")
    }

    @Test fun `square screen with icon-only and long-label actions still builds the buttons`() {
        enabled()
        whenever(source.getValidFor()).thenReturn(null)
        // icon-only (no text branch) + a long label on a small square screen
        whenever(source.getSelectedActions()).thenReturn(listOf(action(text = null, sub = null), action(text = "VeryLongLabel", sub = "sub")))
        val elements = elementsOf(service(), request(round = false, widthDp = 160, heightDp = 160))
        assertThat(iconCount(elements)).isEqualTo(2)
        assertThat(textsOf(elements)).contains("VeryLongLabel")
    }

    // ---- freshness + resource-version wiring ----

    @Test fun `valid-for is written as the freshness interval and the resource version is set`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(60_000L)
        val tile = service().tileRequest(request())
        assertThat(tile.freshnessIntervalMillis).isEqualTo(60_000L)
        assertThat(tile.resourcesVersion).isEqualTo("ActionsTileService")
    }

    @Test fun `null valid-for leaves the freshness interval unset`() {
        enabled()
        whenever(source.getSelectedActions()).thenReturn(listOf(action()))
        whenever(source.getValidFor()).thenReturn(null)
        assertThat(service().tileRequest(request()).freshnessIntervalMillis).isEqualTo(0L)
    }

    // ---- resources request ----

    @Test fun `resources request maps every source drawable id and carries the version`() {
        whenever(source.getResourceReferences(any())).thenReturn(listOf(111, 222))
        val res = service()
            .resourcesRequest(RequestBuilders.ResourcesRequest.Builder().setVersion("ActionsTileService").build())
        assertThat(res.version).isEqualTo("ActionsTileService")
        assertThat(res.idToImageMapping.keys).containsExactly("111", "222")
    }

    // ---- every concrete TileBase subclass wires its own source + resource version ----

    @Test fun `each tile service builds a tile carrying its own resource version`() {
        // All 6 subclasses are identical bar the @Inject source field + resourceVersion string; reflectively set a
        // mocked source of the field's concrete type so onTileRequest runs the real service.source getter.
        val serviceClasses = listOf(
            ActionsTileService::class.java,
            TempTargetTileService::class.java,
            UserActionTileService::class.java,
            RunningModeTileService::class.java,
            SceneTileService::class.java,
            QuickWizardTileService::class.java
        )
        for (cls in serviceClasses) {
            val svc = Robolectric.buildService(cls).get()
            svc.preferences = prefs
            svc.aapsLogger = AAPSLoggerTest()
            enabled()
            val sourceField = cls.declaredFields.first { TileSource::class.java.isAssignableFrom(it.type) }.apply { isAccessible = true }
            val src = Mockito.mock(sourceField.type) as TileSource
            whenever(src.getSelectedActions()).thenReturn(emptyList())
            whenever(src.getValidFor()).thenReturn(null)
            sourceField.set(svc, src)

            val tile = svc.tileRequest(request())

            assertThat(tile.resourcesVersion).isEqualTo(svc.resourceVersion)
        }
    }
}
