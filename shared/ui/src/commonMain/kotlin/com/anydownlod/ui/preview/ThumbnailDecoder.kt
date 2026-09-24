package com.anydownlod.ui.preview

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes a thumbnail the host already fetched. Null when this target cannot decode it. */
internal expect fun decodeThumbnail(bytes: ByteArray): ImageBitmap?
