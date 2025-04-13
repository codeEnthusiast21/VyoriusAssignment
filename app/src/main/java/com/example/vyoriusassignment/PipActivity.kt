package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

        streamUrl = intent.getStringExtra(MainActivity.EXTRA_STREAM_URL) ?: ""
        streamPosition = intent.getLongExtra(MainActivity.EXTRA_STREAM_POSITION, 0)

        if (streamUrl.isEmpty()) {
            finish()
            return
        }

        initViews()
        initPlayer()
        startStream()
        enterPipMode()
    }

    private fun initViews() {
        videoLayout = findViewById(R.id.pip_video_layout)
    }

    private fun initPlayer() {
        try {
            val options = ArrayList<String>().apply {
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
            Log.e("PipActivity", "Error initializing player: ${e.message}", e)
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

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            // Save current position
            streamViewModel.apply {
                currentUrl = streamUrl
                this.isPlaying = true
                mediaPosition = mediaPlayer.time
            }

            // Return to MainActivity
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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