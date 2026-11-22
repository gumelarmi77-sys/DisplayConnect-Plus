package com.displayconnect.plus.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.displayconnect.plus.ble.BleManager
import org.json.JSONObject

class MediaHelper(private val context: Context) {
    private val TAG = "MediaHelper"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeController: MediaController? = null

    var onMediaChanged: ((String, String, Boolean) -> Unit)? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            dispatchCurrentMedia()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            dispatchCurrentMedia()
        }
    }

    fun setActiveController(controller: MediaController?) {
        activeController?.unregisterCallback(mediaCallback)
        activeController = controller
        activeController?.registerCallback(mediaCallback)
        dispatchCurrentMedia()
    }

    fun handleCommand(action: String) {
        val controller = activeController
        when (action) {
            "toggle" -> {
                val state = controller?.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller?.transportControls?.play()
                }
            }
            "next" -> {
                controller?.transportControls?.skipToNext()
            }
            "prev" -> {
                controller?.transportControls?.skipToPrevious()
            }
            "vol_up" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "vol_down" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    fun dispatchCurrentMedia() {
        val controller = activeController
        val metadata = controller?.metadata
        val pbState = controller?.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Tidak Ada Lagu"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Media Player"
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING

        mainHandler.post {
            onMediaChanged?.invoke(title, artist, isPlaying)
        }

        // Format JSON kirim ke ESP32
        try {
            val json = JSONObject().apply {
                put("t", "media")
                put("title", title)
                put("artist", artist)
                put("playing", isPlaying)
            }
            BleManager.sendJson(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting media JSON: ${e.message}")
        }
    }
}
