package com.kayb28.losslesswallpaper.core

enum class WallpaperMode {
    /** Original stream plus a crop hint; no pixel is rewritten by the app. */
    CROP,
    /** Center the complete image over a manually chosen solid background. */
    FIT,
    /** Derive a soft background gradient from the image edges. */
    AUTO_COLOR,
    /** Center the complete image over pure black. */
    BLACK,
    /** Center the complete image over a blurred, enlarged copy. */
    BLUR,
    /** Fill the unused area using stretched outer image pixels. */
    EXTEND_EDGES,
}
