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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.videolan.libvlc.interfaces.IMedia
import android.Manifest
import android.media.MediaScannerConnection
import android.widget.TextView
import org.videolan.libvlc.Media


class MainActivity : AppCompatActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var urlEditText: EditText
    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pipButton: Button
    private var isInPipMode = false
    private lateinit var timerText: TextView
    private var timerHandler: Handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var startTime: Long = 0

    private val streamViewModel: StreamViewModel by viewModels()

    private var isPlaying = false
    private var isRecording = false

    companion object {
        private const val RECORDINGS_DIR = "DCIM/StreamRecordings"


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
        timerText = findViewById(R.id.timer_text)

        setupClickListeners()
    }
    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerText.visibility = View.VISIBLE

        timerRunnable = object : Runnable {
            override fun run() {
                val millis = System.currentTimeMillis() - startTime
                val seconds = (millis / 1000).toInt()
                val minutes = seconds / 60
                val hours = minutes / 60

                timerText.text = String.format("%02d:%02d:%02d",
                    hours,
                    minutes % 60,
                    seconds % 60)

                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerText.visibility = View.GONE
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

        if (!checkStoragePermission()) {
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val recordingsDir = File(storageDir, "StreamRecordings")

            if (!recordingsDir.exists()) {
                if (!recordingsDir.mkdirs()) {
                    Toast.makeText(this, "Failed to create recordings directory", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val recordingFile = File(recordingsDir, "Recording_$timestamp.mp4")
            val recordingPath = recordingFile.absolutePath

            val currentUrl = urlEditText.text.toString().trim()

            // Stop current playback
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            // Create new media with recording configuration
            val media = Media(libVlc, Uri.parse(currentUrl))
            media.setHWDecoderEnabled(true, true)

            // Set media options
            media.addOption(":network-caching=1500")
            media.addOption(":live-caching=1500")
            media.addOption(":rtsp-tcp")
            media.addOption(":sout=#duplicate{dst=display,dst=file{dst='$recordingPath',mux=mp4}}")
            media.addOption(":sout-all")
            media.addOption(":sout-keep")

            // Set media and start playback
            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()

            isRecording = true
            recordButton.text = "Stop Recording"
            startTimer()
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
            isRecording = false
            recordButton.text = "Record"
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
            media.addOption(":network-caching=300")
            media.addOption(":rtsp-tcp")
            media.addOption(":rtsp-frame-buffer-size=500000")
            media.addOption(":clock-jitter=0")
            media.addOption(":live-caching=300")
            media.addOption(":file-caching=300")

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

    private fun initPlayer() {
        try {
            val options = ArrayList<String>().apply {
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                add("--rtsp-tcp")
                add("--network-caching=300")
                add("--live-caching=300")
                add("--file-caching=300")
                add("--clock-jitter=0")
                add("--clock-synchro=0")
                add("--rtsp-frame-buffer-size=500000")
            }

            libVlc = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVlc)
            setupMediaPlayerEvents()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize player", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            val currentUrl = urlEditText.text.toString().trim()

            // Stop recording
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            // Create new media for normal playback
            val media = Media(libVlc, Uri.parse(currentUrl))
            media.setHWDecoderEnabled(true, true)
            media.addOption(":network-caching=1500")
            media.addOption(":live-caching=1500")
            media.addOption(":rtsp-tcp")

            // Resume normal playback
            mediaPlayer.media = media
            media.release()
            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()

            isRecording = false
            recordButton.text = "Record"
            stopTimer()

            // Trigger media scan
            val recordingsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "StreamRecordings")
            MediaScannerConnection.scanFile(
                this,
                arrayOf(recordingsDir.absolutePath),
                null
            ) { _, _ ->
                runOnUiThread {
                    Toast.makeText(this, "Recording saved in DCIM/StreamRecordings", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
                return false
            }
        }
        return true
    }


    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "Storage permission required for recording", Toast.LENGTH_SHORT).show()
            }
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
            stopTimer()
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