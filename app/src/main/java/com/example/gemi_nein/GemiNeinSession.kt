package com.example.gemi_nein

import android.content.Intent
import android.service.voice.VoiceInteractionSession

class GemiNeinSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val intent = Intent(
            this.context,
            MainActivity::class.java
        ).apply {
            putExtra(MainActivity.EXTRA_START_LISTENING, true)
        }

        startAssistantActivity(intent)
    }
}