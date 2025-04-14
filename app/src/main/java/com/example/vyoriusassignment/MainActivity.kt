package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.compareTo
import kotlin.toString

class MainActivity : AppCompatActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var urlEditText: EditText
    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pipButton: Button
    private var isInPipMode = false

    private val streamViewModel: StreamViewModel by viewModels()

    private var isPlaying = false
    private var isRecording = false

    companion object {
        private const val PIP_REQUEST_CODE = 101

        private const val TAG = "MainActivity"
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_STREAM_POSITION = "stream_position"
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
            if (isPlaying) {
                minimizeToPiP()
            } else {
                Toast.makeText(this, "Start streaming first", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun minimizeToPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Save current playback state
                streamViewModel.apply {
                    currentUrl = urlEditText.text.toString().trim()
                    isPlaying = true
                    mediaPosition = mediaPlayer.time
                }

                // Enter PiP mode
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.e(TAG, "Error entering PiP mode: ${e.message}", e)
                Toast.makeText(this, "Failed to enter PiP mode", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun startStream() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, true)

            // Optimize for lower latency
            media.addOption(":network-caching=50")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=100000")
            media.addOption(":clock-jitter=0")
            media.addOption(":live-caching=0")
            media.addOption(":file-caching=0")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, true, false)
            mediaPlayer.play()

            isPlaying = true
            playButton.text = "Stop"

        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream: ${e.message}", e)
            Toast.makeText(this, "Error starting stream", Toast.LENGTH_SHORT).show()
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
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isInPipMode) {
            // Adjust video layout for PiP mode if needed
            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            hideUIElements()

        } else {
            showUIElements()


        }
    }
    private fun hideUIElements() {
        urlEditText.visibility = View.GONE
        playButton.visibility = View.GONE
        recordButton.visibility = View.GONE
        pipButton.visibility = View.GONE
    }

    private fun showUIElements() {
        urlEditText.visibility = View.VISIBLE
        playButton.visibility = View.VISIBLE
        recordButton.visibility = View.VISIBLE
        pipButton.visibility = View.VISIBLE
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
    override fun onResume() {
        super.onResume()
        if (streamViewModel.isPlaying) {
            Handler(Looper.getMainLooper()).postDelayed({
                resumePlayback()
            }, 500)
        }
    }
    private fun resumePlayback() {
        try {
            val url = streamViewModel.currentUrl
            val position = streamViewModel.mediaPosition

            urlEditText.setText(url)

            // Stop any existing playback
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            // Create new media
            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=100")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=500000")

            mediaPlayer.media = media
            media.release()

            // Attach views and play
            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()
            mediaPlayer.time = position

            isPlaying = true
            playButton.text = "Stop"

            // Reset ViewModel state
            streamViewModel.isPlaying = false

        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback: ${e.message}", e)
            Toast.makeText(this, "Error resuming playback", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra("RESUME_STREAM", false)) {
            // Resume immediately without delay
            val position = intent.getLongExtra("STREAM_POSITION", 0)
            resumeStreamFromPip(position)
        }
    }
    private fun resumeStreamFromPip(position: Long) {
        try {
            val url = streamViewModel.currentUrl
            if (url.isEmpty()) return

            urlEditText.setText(url)

            val media = Media(libVlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, true)

            // Optimized options for quick resumption
            media.addOption(":network-caching=50")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=100000")
            media.addOption(":clock-jitter=0")
            media.addOption(":live-caching=0")
            media.addOption(":file-caching=0")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, true, false)
            mediaPlayer.play()
            mediaPlayer.time = position

            isPlaying = true
            playButton.text = "Stop"

            // Reset ViewModel state
            streamViewModel.isPlaying = false

        } catch (e: Exception) {
            Log.e(TAG, "Error resuming stream: ${e.message}", e)
            Toast.makeText(this, "Error resuming stream", Toast.LENGTH_SHORT).show()
        }
    }

}