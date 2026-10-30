package com.kayb28.losslesswallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kayb28.losslesswallpaper.ui.LosslessWallpaperTheme
import com.kayb28.losslesswallpaper.ui.WallpaperApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LosslessWallpaperTheme {
                WallpaperApp()
            }
        }
    }
}
