package com.roadside.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log

class SpeechRecognizer(private val context: Context) {

    private var recognizer: AndroidSpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    companion object {
        fun createSpeechIntent(): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something about your motorcycle issue...")
            }
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit = {}
    ) {
        if (!AndroidSpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service not found on this system")
            return
        }

        stopListening()

        try {
            val speechRec = AndroidSpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speechRec

            val intent = createSpeechIntent()

            speechRec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onReady()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    val message = when (error) {
                        AndroidSpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking closer to the mic."
                        AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. No sound detected."
                        AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        AndroidSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required."
                        else -> "Speech recognition error ($error)"
                    }
                    Log.w("SpeechRecognizer", message)
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                    val recognized = matches?.firstOrNull().orEmpty()
                    if (recognized.isNotBlank()) {
                        onResult(recognized)
                    } else {
                        onError("No speech recognized")
                    }
                }

                // Partial hypotheses are ignored: forwarding them as results turned one spoken
                // sentence into several chat messages (each partial, then the final result).
                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRec.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to start speech recognition", e)
            onError(e.message ?: "Failed to start speech recognition")
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechRecognizer", "Error cleaning up speech recognizer", e)
        } finally {
            recognizer = null
            isListening = false
        }
    }
}
