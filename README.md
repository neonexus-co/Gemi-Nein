# Gemi-nein 🪶

**An open-source alternative to Google Assistant, built with privacy and user control in mind.**

Gemi-nein is an experimental Android voice assistant with a simple goal: give users a practical, extensible alternative to proprietary digital assistants.

It starts with the essentials: recognize speech on-device, interpret commands locally, and use Android's native capabilities to get things done — without requiring a cloud-based AI service for everyday tasks.

The project is a work in progress, but the foundation is in place and ready to grow.

> Less cloud. Less overhead. More control.

## ✨ Features

### Implemented

- **Power-button invocation** — Launch Gemi-nein through Android's default digital assistant integration.
- **On-device speech recognition** — Use Android's on-device speech recognition API where supported.
- **Local command parsing** — Interpret recognized speech using Kotlin code running on the device.
- **Timers** — Set timers through Android's native clock application.
- **Alarms** — Set alarms through Android's native clock application.
- **Spoken confirmations** — Get audio feedback through Android Text-to-Speech.
- **Lock-screen support** — Invoke the assistant and issue commands while the device is locked.
- **Lifecycle cleanup** — Dismiss the temporary assistant interface after a command completes, without leaving a lingering session window.
- **No cloud AI dependency for basic commands** — The current timer and alarm functionality doesn't require a remote LLM or API key.

### Project goals

Gemi-nein aims to be:

- **Local-first:** Prefer on-device processing wherever practical.
- **Lightweight:** Keep the core assistant focused and maintainable.
- **User-controlled:** Use Android's supported assistant integration.
- **Extensible:** Make adding commands and device capabilities straightforward.
- **Open source:** Encourage inspection, modification, and community contributions.

## 🚧 Roadmap

Potential areas for future development:

- [ ] Expand supported voice commands.
- [ ] Add more local device actions and system integrations.
- [ ] Improve command interpretation and error recovery.
- [ ] Explore hands-free activation and wake-word detection.
- [ ] Expand automated testing and Android device compatibility.
- [ ] Improve accessibility and user customization.
- [ ] Establish contribution guidelines and a broader testing process.

These are goals, not commitments to features that already exist.

## 📱 Compatibility

Gemi-nein is an Android application developed and initially tested on a **Google Pixel 7 Pro**.

| Component | Technology                               |
|---|------------------------------------------|
| Language | Kotlin                                   |
| Platform | Android                                  |
| Speech recognition | Android on-device `SpeechRecognizer` API |
| Command interpretation | Local Kotlin logic                       |
| Timers and alarms | Android clock intents                    |
| Spoken feedback | Android Text-to-Speech                   |
| Application ID | `co.neonexus.geminein`                   |

The current implementation requires Android 12 or newer for its on-device speech recognition path. Recognition availability also depends on the device's installed speech services and supported language models.

Assistant gestures, lock-screen behavior, and clock integration may vary across Android versions and manufacturers.

## 🛠️ Getting started

### Requirements

- Android Studio
- Android 12 or newer on a compatible device
- A compatible on-device speech recognition service
- A clock application that handles Android timer and alarm intents

### Build from source

Clone the repository:

```bash
git clone https://github.com/neonexus-co/Gemi-Nein.git
cd Gemi-Nein
```

Then:

1. Open the project in Android Studio.
2. Allow Gradle to sync and download the project's dependencies.
3. Connect an Android device with USB debugging enabled.
4. Select the `app` run configuration.
5. Build and run the application.

The required Gradle, Android Gradle Plugin, and JDK versions are determined by the project configuration.

### Grant microphone permission

On first use, Gemi-nein requests microphone permission. Grant it to enable speech recognition.

### Set Gemi-nein as your default digital assistant

On a supported device:

1. Open Android **Settings**.
2. Navigate to **Apps → Default apps → Digital assistant app**. The exact labels may vary.
3. Select **Gemi-nein**.
4. Use the configured assistant gesture, such as long-pressing the power button on a supported device.

If Gemi-nein isn't listed, check that the app is installed correctly and its voice-interaction service is registered.

### Try it out

Invoke Gemi-nein and try a supported command:

- "Set a timer for five minutes."
- "Set an alarm for seven thirty AM."

The application recognizes the speech, parses the command locally, and passes the request to Android's clock application. Gemi-nein then provides spoken confirmation when the action succeeds.

Supported phrasing depends on the current command parser and the speech recognizer's transcription.

## 🔒 Privacy and architecture

Privacy is a core design goal.

The current implementation uses Android's on-device speech recognition API and interprets supported commands locally. Timer and alarm requests are dispatched through Android intents rather than a remote AI service.

**Basic timer and alarm commands do not require a cloud LLM.**

A few important details:

- On-device recognition depends on compatible system services and language support.
- Speech synthesis depends on the installed Text-to-Speech engine.
- Android's clock application handles the resulting timer and alarm requests.
- Future integrations may have different privacy implications and should be evaluated accordingly.

The goal is to minimize unnecessary data transmission while working within Android's supported APIs.

## 🧩 How it works

The current command flow is straightforward:

1. **Invocation:** Android invokes Gemi-nein through its voice-interaction integration.
2. **Presentation:** The voice-interaction session launches the assistant activity.
3. **Recognition:** The speech recognizer converts spoken input into text.
4. **Interpretation:** Local Kotlin logic identifies a supported command.
5. **Execution:** Gemi-nein sends a timer or alarm request to Android's clock application.
6. **Confirmation:** Text-to-Speech provides feedback, and the temporary assistant UI closes.

The main components include:

| Component | Responsibility |
|---|---|
| `MainActivity.kt` | Assistant UI, speech recognition, command dispatch, and spoken feedback |
| `AssistantCommandParser.kt` | Parses recognized text into supported commands |
| `ClockActions.kt` | Dispatches timer and alarm requests |
| `GemiNeinVoiceInteractionService.kt` | Registers the voice-interaction service |
| `GemiNeinSessionService.kt` | Creates voice-interaction sessions |
| `GemiNeinSession.kt` | Manages session presentation and launches the assistant activity |

These names describe the current implementation and may change as the project evolves.

## 🤝 Contributing

Contributions, bug reports, and ideas are welcome!

Useful areas for contributions include:

- New command parsers and local actions
- Android lifecycle and device compatibility testing
- Privacy-preserving integrations
- Automated tests
- Documentation and setup improvements
- Accessibility and usability enhancements

When submitting a change, include a clear description of the behavior being changed and, where relevant, the device and Android version used for testing.

For larger changes, opening an issue first is a good way to discuss the approach before investing in implementation.

## 📜 License

Gemi-nein is licensed under the **GNU General Public License, version 3 or (at your option) any later version** — `GPL-3.0-or-later`.

You may use, study, modify, and redistribute the software under the terms of that license. Distribution of covered modified versions is subject to the GPL's applicable source-code and licensing requirements.

See the [`LICENSE`](LICENSE) file for the complete license text.

Official license information: [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html).

## 💬 Why "Gemi-nein"?

Honestly, I considered a few options here. 
- **Boogle Assistant** - As in the ghost of google assistants past
- **Ok, Actually** - A reference to the original "Ok, Google" command with a little added snark 
etc... 

I finally landed on **Gemi-nein** as a tounge in cheek reference to what's currently being pushed heavily in the Android ecosystem along with a play on the word nein (meaning no in German). A little statement of intent and a reminder that alternatives are worth building. While I understand all the motivations to switch over to an architecture which allows for better harvesting of our data, I'd like to ensure that we maintain the ability to opt out. Respect the hustle tho.

**Gemi-nein** is an independent project and is not affiliated with or endorsed by Google or any other assistant provider.

---

**Gemi-nein — your phone, your commands, your rules.** 🪶
