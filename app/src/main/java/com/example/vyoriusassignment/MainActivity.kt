package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var urlEditText: EditText
    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pipButton: Button

    private var isPlaying = false
    private var isRecording = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initPlayer()
    }

    private fun initViews() {
        videoLayout = findViewById(R.id.video_layout)
        urlEditText = findViewById(R.id.url_edit_text)
        playButton = findViewById(R.id.play_button)
        recordButton = findViewById(R.id.record_button)
        pipButton = findViewById(R.id.pip_button)

        setupClickListeners()
    }

    private fun initPlayer() {
        try {
            val options = ArrayList<String>().apply {
                add("-vvv") // verbose logging
                add("--aout=opensles")
                add("--audio-time-stretch")
                add("--avcodec-skiploopfilter")
                add("--avcodec-skip-frame")
                add("--avcodec-skip-idct")
                add("--rtsp-tcp")
                add("--network-caching=100")
                add("--live-caching=100")
                add("--sout-mux-caching=100")
                add("--clock-jitter=0")
                add("--clock-synchro=0")
                add("--no-audio")
            }

            libVlc = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVlc)
            setupMediaPlayerEvents()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize player", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMediaPlayerEvents() {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "Media is playing")
                    runOnUiThread {
                        isPlaying = true
                        playButton.text = "Stop"
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Playback error occurred")
                    runOnUiThread {
                        handlePlaybackError()
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    Log.d(TAG, "Buffering: ${event.buffering}%")
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "End reached")
                    runOnUiThread {
                        resetPlayback()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        playButton.setOnClickListener {
            if (!isPlaying) startStream() else stopStream()
        }

        recordButton.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }

        pipButton.setOnClickListener {
            if (isPlaying) enterPipMode() else {
                Toast.makeText(this, "Start streaming first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startStream() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Release any existing media
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)

            // Add media options
            media.addOption(":network-caching=100")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=500000")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()

            Log.d(TAG, "Starting stream: $url")
            Toast.makeText(this, "Connecting to stream...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream: ${e.message}", e)
            handlePlaybackError()
        }
    }

    private fun stopStream() {
        try {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            resetPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping stream: ${e.message}", e)
        }
    }

    private fun handlePlaybackError() {
        Toast.makeText(this, "Playback error occurred", Toast.LENGTH_SHORT).show()
        resetPlayback()
    }

    private fun resetPlayback() {
        isPlaying = false
        playButton.text = "Play"
        if (isRecording) {
            stopRecording()
        }
    }

    private fun startRecording() {
        if (!isPlaying) {
            Toast.makeText(this, "Start streaming first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val recordingPath = "${getExternalFilesDir(Environment.DIRECTORY_MOVIES)}/Recording_$timestamp.mp4"

            Log.d(TAG, "Starting recording to: $recordingPath")
            mediaPlayer.record(recordingPath)

            isRecording = true
            recordButton.text = "Stop Recording"
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaPlayer.record(null)
            isRecording = false
            recordButton.text = "Record"
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.e(TAG, "Error entering PiP mode: ${e.message}", e)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        videoLayout.visibility = if (isInPictureInPictureMode) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer.release()
            libVlc.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}", e)
        }
    }
}