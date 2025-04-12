package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private companion object {
        private const val REQUEST_PERMISSIONS = 1
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var urlEditText: EditText
    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pipButton: Button

    private var isStreaming = false
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        checkAndRequestPermissions()
    }

    private fun initViews() {
        videoLayout = findViewById(R.id.video_layout)
        urlEditText = findViewById(R.id.url_edit_text)
        playButton = findViewById(R.id.play_button)
        recordButton = findViewById(R.id.record_button)
        pipButton = findViewById(R.id.pip_button)
    }

    private fun initializePlayer() {
        try {
            val options = ArrayList<String>().apply {
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                add("--rtsp-tcp")
            }

            libVlc = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVlc)
            setupClickListeners()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing player: ${e.message}")
            Toast.makeText(this, "Failed to initialize player", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        playButton.setOnClickListener { playStream() }
        recordButton.setOnClickListener { toggleRecording() }
        pipButton.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isNotEmpty() && isStreaming) {
                startPiPMode(url)
            } else {
                Toast.makeText(this, "Start streaming first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playStream() {
        val rtspUrl = urlEditText.text.toString().trim()
        if (rtspUrl.isEmpty()) {
            Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer.stop()
            mediaPlayer.detachViews()

            val media = Media(libVlc, Uri.parse(rtspUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1000")

            mediaPlayer.media = media
            media.release()

            mediaPlayer.attachViews(videoLayout, null, false, false)
            mediaPlayer.play()

            isStreaming = true
            Toast.makeText(this, "Playing stream...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing stream: ${e.message}")
            Toast.makeText(this, "Error playing stream", Toast.LENGTH_SHORT).show()
            isStreaming = false
        }
    }

    private fun toggleRecording() {
        if (!isRecording) startRecording() else stopRecording()
    }

    private fun startRecording() {
        if (!isStreaming) {
            Toast.makeText(this, "Please start streaming first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val recordingsDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val recordingFile = File(recordingsDir, "RTSP_Recording_$timestamp.mp4")

            val media = Media(libVlc, urlEditText.text.toString().trim())
            media.addOption(":sout=#duplicate{dst=display,dst=file{dst=${recordingFile.absolutePath}}}")
            media.addOption(":sout-keep")

            mediaPlayer.stop()
            mediaPlayer.setMedia(media)
            mediaPlayer.play()

            isRecording = true
            recordButton.text = "Stop Recording"
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

            media.release()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting recording: ${e.message}")
            Toast.makeText(this, "Error starting recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            val media = Media(libVlc, urlEditText.text.toString().trim())
            mediaPlayer.stop()
            mediaPlayer.setMedia(media)
            mediaPlayer.play()

            isRecording = false
            recordButton.text = "Record"
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()

            media.release()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping recording: ${e.message}")
            Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPiPMode(url: String) {
        val intent = Intent(this, PipActivity::class.java).apply {
            putExtra("rtsp_url", url)
        }
        startActivity(intent)
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsToRequest = mutableListOf<String>()

            // For Android 13+ (API 33), use the new media permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                }
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                }
            } else {
                // For older versions, use storage permissions
                REQUIRED_PERMISSIONS.forEach { permission ->
                    if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(permission)
                    }
                }
            }

            when {
                permissionsToRequest.isEmpty() -> {
                    Log.d("Permissions", "All permissions granted")
                    initializePlayer()
                }
                else -> {
                    Log.d("Permissions", "Requesting permissions: $permissionsToRequest")
                    requestPermissions(
                        permissionsToRequest.toTypedArray(),
                        REQUEST_PERMISSIONS
                    )
                }
            }
        } else {
            initializePlayer()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("Permissions", "onRequestPermissionsResult: ${permissions.zip(grantResults.toTypedArray())}")

        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                if (allGranted) {
                    Log.d("Permissions", "All permissions granted in result")
                    initializePlayer()
                } else {
                    // Check if we should show the rationale for any permission
                    var showRationale = false
                    permissions.forEachIndexed { index, permission ->
                        if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                            showRationale = showRationale || shouldShowRequestPermissionRationale(permission)
                        }
                    }

                    if (showRationale) {
                        Log.d("Permissions", "Showing rationale dialog")
                        showPermissionExplanationDialog()
                    } else {
                        Log.d("Permissions", "Showing settings dialog")
                        showSettingsDialog()
                    }
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        Log.d("Dialog", "Showing permission explanation dialog")
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs access to storage to save recordings and network access for streaming.")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                checkAndRequestPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSettingsDialog() {
        Log.d("Dialog", "Showing settings dialog")
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Please enable permissions in Settings to use this app.")
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("Settings", "Error opening settings: ${e.message}")
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onStart() {
        super.onStart()
        if (::mediaPlayer.isInitialized && ::videoLayout.isInitialized) {
            mediaPlayer.attachViews(videoLayout, null, false, false)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.detachViews()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        if (::libVlc.isInitialized) {
            libVlc.release()
        }
    }
}