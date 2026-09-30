@file:Suppress("DEPRECATION")

package app.aaps.wear.tile

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import javax.inject.Inject

private const val SPACING_ROWS_DP = 4f
private const val ICON_SIZE_DP = 26f
private const val BUTTON_PADDING_DP = 4f

/**
 * Data source for Wear OS tiles.
 *
 * Tiles are interactive glanceable surfaces that display information and actions
 * directly on the watch face carousel. TileSource defines the contract for
 * providing tile content, resources, and refresh behavior.
 *
 * Implementations provide:
 * - Actions to display (buttons with icons and text)
 * - Resource references for images
 * - Refresh interval (how long tile data remains valid)
 */
interface TileSource {

    /**
     * Get list of drawable resource IDs used by this tile.
     *
     * These resources are bundled with the tile and made available
     * for rendering. Typically includes button icons and status indicators.
     *
     * @param resources Android resources for accessing drawables
     * @return List of drawable resource IDs (e.g., R.drawable.ic_bolus)
     */
    fun getResourceReferences(resources: Resources): List<Int>

    /**
     * Get list of actions to display on the tile.
     *
     * Actions are rendered as interactive buttons. The tile layout
     * automatically arranges 1-4 actions in appropriate grid patterns:
     * - 1 action: Single centered button
     * - 2 actions: Two buttons side-by-side
     * - 3 actions: One on top, two on bottom
     * - 4 actions: 2x2 grid
     *
     * @return List of 1-4 actions to display, or empty for no actions
     */
    fun getSelectedActions(): List<Action>

    /**
     * Get validity duration for tile data in milliseconds.
     *
     * Determines how long the system can cache tile data before
     * requesting a refresh. Shorter intervals ensure fresher data
     * but consume more battery.
     *
     * @return Duration in milliseconds, or null for no automatic refresh
     */
    fun getValidFor(): Long?
}

/**
 * Defines an interactive action button on a Wear OS tile.
 *
 * Actions are rendered as circular buttons with icons and optional text.
 * When tapped, they launch the specified activity with optional data payload.
 *
 * @param buttonText Primary text label displayed on the button (e.g., "Bolus")
 * @param buttonTextSub Secondary text label displayed below primary (e.g., "5.2U")
 * @param activityClass Fully qualified class name of activity to launch (e.g., "app.aaps.wear.MyActivity")
 * @param iconRes Drawable resource ID for the button icon
 * @param action Optional event data to pass to the launched activity
 * @param message Optional message string to pass to the launched activity
 */
open class Action(
    val buttonText: String? = null,
    val buttonTextSub: String? = null,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
    val action: EventData? = null,
    val message: String? = null,
)

/**
 * Wear control state indicating whether remote control is enabled and data is available.
 *
 * Determines what content the tile displays:
 * - ENABLED: Normal operation, show action buttons
 * - DISABLED: Wear control not enabled in preferences, show message
 * - NO_DATA: Wear control enabled but no data received from phone, show message
 */
enum class WearControl {
    /** No data received from phone yet */
    NO_DATA,

    /** Wear control enabled and data available, show actions */
    ENABLED,

    /** Wear control disabled in app preferences */
    DISABLED
}

abstract class TileBase : TileService() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var aapsLogger: AAPSLogger

    abstract val resourceVersion: String
    abstract val source: TileSource

    /** Short title shown at the top of the tile */
    @StringRes open val titleRes: Int? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> = serviceScope.future {
        val actionsSelected = getSelectedActions()
        val wearControl = getWearControl()
        val deviceParams = requestParams.deviceConfiguration

        // Build layout using protolayout (non-deprecated)
        val layoutElement = layout(wearControl, actionsSelected, deviceParams)

        // Create protolayout Timeline from LayoutElement
        val protoTimeline = Timeline.fromLayoutElement(layoutElement)

        val tile = Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTileTimeline(protoTimeline)

        val validFor = validFor()
        if (validFor != null) {
            tile.setFreshnessIntervalMillis(validFor)
        }
        tile.build()
    }

    private fun getSelectedActions(): List<Action> {
        // TODO check why thi scan not be don in scope of the coroutine
        return source.getSelectedActions()
    }

    private fun validFor(): Long? {
        return source.getValidFor()
    }

    @Deprecated("Deprecated in TileService but still required for now")
    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        // Build resources using tiles (Resources are simple references, no complex UI)
        ResourceBuilders.Resources.Builder()
            .setVersion(resourceVersion)
            .apply {
                source.getResourceReferences(resources).forEach { resourceId ->
                    addIdToImageMapping(
                        resourceId.toString(),
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(resourceId)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()
    }

    /**
     * Build the tile layout based on wear control state and selected actions.
     *
     * Uses the Material 3 Expressive tile components (protolayout-material3), the same style as
     * the system tiles, including the device dynamic color theme:
     * - Title slot: short tile name
     * - Main slot: message (DISABLED / NO_DATA / no configuration) or 1-4 action buttons:
     *   - 1 action: single button
     *   - 2 actions: two buttons side-by-side
     *   - 3 actions: one on top row, two on bottom row
     *   - 4 actions: 2x2 grid
     *
     * @param wearControl Current wear control state
     * @param actions List of actions to display (0-4 actions)
     * @param deviceParameters Screen dimensions and shape
     * @return Layout element to render
     */
    private fun layout(wearControl: WearControl, actions: List<Action>, deviceParameters: DeviceParameters): LayoutElement =
        materialScope(this, deviceParameters, allowDynamicTheme = true) {
            val title = titleRes?.let { getString(it) }
            primaryLayout(
                titleSlot = title?.let { { text(it.layoutString) } },
                mainSlot = {
                    when {
                        wearControl == WearControl.DISABLED -> message(getString(R.string.wear_control_not_enabled))
                        wearControl == WearControl.NO_DATA  -> message(getString(R.string.wear_control_no_data))
                        actions.isEmpty()                   -> message(getString(R.string.tile_no_config))
                        else                                -> actionGrid(actions.take(4))
                    }
                }
            )
        }

    private fun MaterialScope.message(message: String): LayoutElement =
        text(message.layoutString, typography = Typography.BODY_MEDIUM, maxLines = 3)

    private fun MaterialScope.actionGrid(actions: List<Action>): LayoutElement {
        val rows = when (actions.size) {
            1, 2 -> listOf(actions)
            3    -> listOf(actions.subList(0, 1), actions.subList(1, 3))
            else -> listOf(actions.subList(0, 2), actions.subList(2, 4))
        }
        if (rows.size == 1) return actionRow(rows[0])
        return Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(actionRow(rows[0]))
            .addContent(Spacer.Builder().setHeight(dp(SPACING_ROWS_DP)).build())
            .addContent(actionRow(rows[1]))
            .build()
    }

    private fun MaterialScope.actionRow(actions: List<Action>): LayoutElement =
        buttonGroup {
            actions.forEach { action -> buttonGroupItem { actionButton(action) } }
        }

    /**
     * Material 3 button for a tile action: icon (tinted with the theme) above the label
     * and optional secondary label, launching the action's activity on tap.
     */
    private fun MaterialScope.actionButton(action: Action): LayoutElement {
        // Light secondary color with dark content, like the system tiles (e.g. Timer)
        val colors = ButtonColors(
            containerColor = colorScheme.secondary,
            iconColor = colorScheme.onSecondary,
            labelColor = colorScheme.onSecondary,
            secondaryLabelColor = colorScheme.onSecondary
        )
        val clickable = Clickable.Builder()
            .setId(action.buttonText ?: action.activityClass)
            .setOnClick(doAction(action))
            .build()
        return iconButton(
            onClick = clickable,
            iconContent = {
                val content = Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(icon(action.iconRes.toString(), width = dp(ICON_SIZE_DP), height = dp(ICON_SIZE_DP), tintColor = colors.iconColor))
                action.buttonText?.let {
                    content.addContent(text(it.layoutString, typography = Typography.LABEL_MEDIUM, color = colors.labelColor, maxLines = 1))
                }
                action.buttonTextSub?.let {
                    content.addContent(text(it.layoutString, typography = Typography.LABEL_SMALL, color = colors.secondaryLabelColor, maxLines = 1))
                }
                content.build()
            },
            width = expand(),
            height = expand(),
            shape = shapes.large,
            colors = colors,
            contentPadding = Padding.Builder().setAll(dp(BUTTON_PADDING_DP)).build(),
            modifier = LayoutModifier.contentDescription(listOfNotNull(action.buttonText, action.buttonTextSub).joinToString(" "))
        )
    }

    private fun doAction(action: Action): ActionBuilders.Action {
        val builder = ActionBuilders.AndroidActivity.Builder()
            .setClassName(action.activityClass)
            .setPackageName(this.packageName)
        if (action.action != null) {
            val actionString = ActionBuilders.AndroidStringExtra.Builder().setValue(action.action.serialize()).build()
            builder.addKeyToExtraMapping(DataLayerListenerServiceWear.KEY_ACTION, actionString)
        }
        if (action.message != null) {
            val message = ActionBuilders.AndroidStringExtra.Builder().setValue(action.message).build()
            builder.addKeyToExtraMapping(DataLayerListenerServiceWear.KEY_MESSAGE, message)
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(builder.build())
            .build()
    }

    private fun getWearControl(): WearControl {
        if (preferences.getIfExists(BooleanKey.WearControl) == null) {
            return WearControl.NO_DATA
        }
        val wearControlPref = preferences.get(BooleanKey.WearControl)
        if (wearControlPref) {
            return WearControl.ENABLED
        }
        return WearControl.DISABLED
    }

}
