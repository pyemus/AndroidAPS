package app.aaps.core.interfaces.configuration

@Suppress("PropertyName")
interface Config {

    val SUPPORTED_NS_VERSION: Int
    val APS: Boolean
    val AAPSCLIENT: Boolean // aapsclient || aapsclient2
    val AAPSCLIENT1: Boolean // aapsclient
    val AAPSCLIENT2: Boolean // aapsclient2
    val PUMPCONTROL: Boolean
    val PUMPDRIVERS: Boolean
    val FLAVOR: String
    val VERSION_NAME: String
    val HEAD: String
    val COMMITTED: Boolean
    val BUILD_VERSION: String
    val REMOTE: String
    val BUILD_TYPE: String
    val VERSION: String
    val APPLICATION_ID: String
    val DEBUG: Boolean
    val currentDeviceModelString: String
    val appName: Int

    var appInitialized: Boolean

    fun isDev(): Boolean
    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isUnfinishedMode(): Boolean

    fun showUserActionsOnWatchOnly(): Boolean
    fun ignoreNightscoutV3Errors(): Boolean
    fun doNotSendSmsOnProfileChange(): Boolean
    fun enableAutotune(): Boolean

    /**
     * Objectives do not restrict any feature in this build. Progress is still
     * tracked and shown, it just isn't a prerequisite for anything.
     */
    fun skipObjectives(): Boolean

    fun enableOmnipodDriftCompensation(): Boolean

    /**
     * Disable LeakCanary (memory leaks detection). By default it's enabled in DEBUG builds.
     */
    fun disableLeakCanary(): Boolean
}