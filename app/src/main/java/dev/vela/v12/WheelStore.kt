package dev.vela.v12
import android.content.Context

/** Stores only the last verified wheel identity, never telemetry or commands. */
internal class WheelStore(context: Context, file: String = "vela_preferences") {
    private val prefs=context.getSharedPreferences(file,Context.MODE_PRIVATE)
    fun load(): WheelDevice? {
        val address=prefs.getString("wheel_address",null)
        return if(SavedWheelFormat.validAddress(address)) WheelDevice(address!!,prefs.getString("wheel_name","V12 HT") ?: "V12 HT",0) else null
    }
    fun saveVerified(device: WheelDevice) {
        require(SavedWheelFormat.validAddress(device.address))
        prefs.edit().putString("wheel_address",device.address).putString("wheel_name",device.name.take(100)).apply()
    }
    fun forget() {prefs.edit().remove("wheel_address").remove("wheel_name").apply()}
}
