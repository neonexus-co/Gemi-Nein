
package com.example.gemi_nein

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

class GemiNeinSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)

        // The assistant UI is provided by MainActivity.
        // Prevent Android from creating a second session UI window.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val intent = Intent(this.context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_LISTENING, true)
        }

        startAssistantActivity(intent)
    }
}
