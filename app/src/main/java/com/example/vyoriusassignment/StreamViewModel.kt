package com.example.vyoriusassignment

import androidx.lifecycle.ViewModel

class StreamViewModel : ViewModel() {
    var currentUrl: String = ""
    var isPlaying = false
    var mediaPosition: Long = 0
}