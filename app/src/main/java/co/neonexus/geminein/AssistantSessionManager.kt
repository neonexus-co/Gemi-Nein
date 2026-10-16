
package co.neonexus.geminein

import android.app.Activity
import android.util.Log

class AssistantSessionManager(
    private val activity: Activity
) {
    companion object {
        private const val TAG = "GemiNeinVoice"
    }

    var isAssistantInvocation: Boolean = false
        private set

    fun startInvocation() {
        isAssistantInvocation = true
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)

        Log.i(TAG, "Assistant invocation started")
    }

    fun updateInvocation(isAssistantInvocation: Boolean) {
        this.isAssistantInvocation = isAssistantInvocation

        Log.i(
            TAG,
            "Assistant invocation updated: active=$isAssistantInvocation"
        )
    }

    fun handleSpeechOutputFinished(
        utteranceId: String?,
        startListening: () -> Unit
    ) {
        Log.i(TAG, "Handling speech completion: utteranceId=$utteranceId")

        if (utteranceId == SpeechOutput.UTTERANCE_CONTINUE) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Log.i(TAG, "Speech finished; requesting listening restart")
                startListening()
            } else {
                Log.w(
                    TAG,
                    "Skipping listening restart: " +
                            "activity is finishing or destroyed"
                )
            }
        } else {
            Log.d(TAG, "Speech finished; ending assistant invocation")
            finishInvocation()
        }
    }

    fun finishInvocation() {
        Log.d(
            TAG,
            "finishInvocation: " +
                    "isAssistantInvocation=$isAssistantInvocation, " +
                    "isFinishing=${activity.isFinishing}"
        )

        if (isAssistantInvocation && !activity.isFinishing) {
            Log.d(TAG, "Finishing assistant activity")
            activity.finishAndRemoveTask()
        }
    }
}
