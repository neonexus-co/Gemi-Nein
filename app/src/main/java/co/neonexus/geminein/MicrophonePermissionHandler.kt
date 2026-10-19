
package co.neonexus.geminein

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MicrophonePermissionHandler(
    private val activity: Activity,
    private val startListening: () -> Unit
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
        private const val MICROPHONE_REQUEST = 100
    }

    fun checkPermissionAndStartListening() {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        Log.i(
            TAG,
            "checkMicrophonePermission: granted=$permissionGranted"
        )

        if (permissionGranted) {
            startListening()
        } else {
            Log.i(TAG, "Requesting microphone permission")

            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_REQUEST
            )
        }
    }
}
