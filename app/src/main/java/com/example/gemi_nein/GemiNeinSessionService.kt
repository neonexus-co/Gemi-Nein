package com.example.gemi_nein

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class GemiNeinSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return GemiNeinSession(this)
    }
}