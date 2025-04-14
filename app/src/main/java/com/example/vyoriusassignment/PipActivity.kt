package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.or

class PipActivity : AppCompatActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private val streamViewModel: StreamViewModel by viewModels()

    private var streamUrl: String = ""
    private var streamPosition: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pip)

        // Get stream info immediately
        streamUrl = intent.getStringExtra(MainActivity.EXTRA_STREAM_URL) ?: ""
        streamPosition = intent.getLongExtra(MainActivity.EXTRA_STREAM_POSITION, 0)

        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        // Initialize everything before entering PiP
        initViews()
        initPlayer()

        // Start stream with optimized options
        startStreamOptimized()

        // Enter PiP immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
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
                Log.e("PipActivity", "Error entering PiP mode: ${e.message}", e)
            }
        }
    }

    private fun initViews() {
        videoLayout = findViewById(R.id.pip_video_layout)
    }

    private fun initPlayer() {
        try {
            val options = ArrayList<String>().apply {
                add("--rtsp-tcp")
                add("--network-caching=50")  // Reduced from 100
                add("--live-caching=50")     // Reduced from 100
                add("--sout-mux-caching=50") // Reduced from 100
                add("--clock-jitter=0")
                add("--clock-synchro=0")
                add("--no-audio")
                add("--low-delay")           // Added for lower latency
                add("--real-time")           // Added for real-time priority
                add("--no-video-title-show") // Remove unnecessary overlays
            }

            libVlc = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVlc)
            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)
            setupMediaPlayerEvents()

        } catch (e: Exception) {
            Log.e("PipActivity", "Error initializing player: ${e.message}", e)
            finish()
        }
    }

    private fun startStreamOptimized() {
        try {
            val media = Media(libVlc, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, true) // Force hardware decoding

            media.addOption(":network-caching=50")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=100000") // Reduced buffer size
            media.addOption(":clock-jitter=0")
            media.addOption(":live-caching=0")
            media.addOption(":file-caching=0")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, true, false)
            mediaPlayer.play()
            mediaPlayer.time = streamPosition

        } catch (e: Exception) {
            Log.e("PipActivity", "Error starting stream: ${e.message}", e)
            finish()
        }
    }

    private fun setupMediaPlayerEvents() {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    Log.d("PipActivity", "Media is playing")
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e("PipActivity", "Playback error occurred")
                    finish()
                }
            }
        }
    }

    private fun startStream() {
        try {
            val media = Media(libVlc, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)

            media.addOption(":network-caching=100")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=500000")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()
            mediaPlayer.time = streamPosition

        } catch (e: Exception) {
            Log.e("PipActivity", "Error starting stream: ${e.message}", e)
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            // Save current position and URL
            val currentPosition = mediaPlayer.time
            streamViewModel.apply {
                currentUrl = streamUrl
                isPlaying = true
                mediaPosition = currentPosition
            }

            // Clean up resources
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            // Return to MainActivity with explicit playback request
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("RESUME_STREAM", true)
                putExtra("STREAM_POSITION", currentPosition)
            }
            startActivity(intent)
            finish()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer.release()
            libVlc.release()
        } catch (e: Exception) {
            Log.e("PipActivity", "Error releasing resources: ${e.message}", e)
        }
    }
}