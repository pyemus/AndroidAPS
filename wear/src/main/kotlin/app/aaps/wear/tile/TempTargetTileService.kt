package app.aaps.wear.tile

import app.aaps.wear.R
import app.aaps.wear.tile.source.TempTargetSource
import dagger.android.AndroidInjection
import javax.inject.Inject

class TempTargetTileService : TileBase() {

    @Inject lateinit var tempTargetSource: TempTargetSource

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override val resourceVersion = "TempTargetTileService"
    override val titleRes = R.string.tile_title_temp_target
    override val source get() = tempTargetSource
}
