package app.aaps.core.interfaces.alerts

/**
 * Rings an alarm on the paired watch, and rings the phone when the watch does not confirm.
 *
 * Exists so automation's "alarm on watch" action does not have to be Android code. The Android
 * implementation sends the alarm over the wear data layer and keeps two deadlines: one for the
 * watch to confirm it got the message, one for the wearer to dismiss the alarm. When either passes,
 * the phone plays its own loud alarm. That fallback is the whole point: a night-time low that is
 * only on a watch that is off, out of range or ignored must still wake somebody.
 */
interface WatchAlarm {

    /**
     * True only on a platform that can reach a watch at all. It gates whether the action is offered;
     * it never means "a watch is connected right now".
     */
    val isAvailable: Boolean

    /**
     * Starts one alarm with [text] that vibrates for [durationSeconds]. A new call replaces an alarm
     * that is still pending. Never throws; failures end in the phone alarm, not in the caller.
     */
    fun raise(text: String, durationSeconds: Int)
}
