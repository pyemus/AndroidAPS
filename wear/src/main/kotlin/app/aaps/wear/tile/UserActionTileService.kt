package app.aaps.wear.tile

import app.aaps.wear.R
import app.aaps.wear.tile.source.UserActionSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class UserActionTileService : TileBase() {

    @Inject lateinit var userActionSource: UserActionSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "UserActionTileService"
    override val titleRes = R.string.tile_title_user_action
    override val source get() = userActionSource
}
