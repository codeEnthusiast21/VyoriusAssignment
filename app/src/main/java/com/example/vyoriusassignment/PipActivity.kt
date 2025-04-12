package com.example.vyoriusassignment

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.vyoriusassignment.databinding.ActivityPipBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*



@RequiresApi(Build.VERSION_CODES.O)
class PipActivity : AppCompatActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoSurface: SurfaceView
    private var rtspUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pip)

        rtspUrl = intent.getStringExtra("rtsp_url") ?: ""
        videoSurface = findViewById(R.id.pipVideoSurface)

        initVLC()
        if (rtspUrl.isNotEmpty()) {
            startPlayback(rtspUrl)
        }
    }

    private fun initVLC() {
        val options = ArrayList<String>().apply {
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("--rtsp-tcp")
        }

        libVlc = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVlc)
    }

    private fun startPlayback(url: String) {
        try {
            val media = Media(libVlc, Uri.parse(url))
            mediaPlayer.media = media
            media.release()

            mediaPlayer.vlcVout.setVideoView(videoSurface)
            mediaPlayer.vlcVout.attachViews()
            mediaPlayer.play()

            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        } catch (e: Exception) {
            finish()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, config)
        if (!isInPictureInPictureMode) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.vlcVout.detachViews()
        mediaPlayer.release()
        libVlc.release()
    }
}