package app.aaps.wear.tile

import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.wear.R
import app.aaps.wear.tile.source.UserActionSource
import dev.zacsweers.metro.Inject

class UserActionTileService : TileBase() {

    @Inject lateinit var userActionSource: UserActionSource

    // Not derived from WearMetroService, do injection here
    override fun onCreate() {
        injectMetroMembers(this)
        super.onCreate()
    }

    override val resourceVersion = "UserActionTileService"
    override val titleRes = R.string.tile_title_user_action
    override val source get() = userActionSource
}
