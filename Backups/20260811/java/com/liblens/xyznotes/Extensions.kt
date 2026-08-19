package com.liblens.xyznotes

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import android.widget.ImageButton
import androidx.core.content.ContextCompat

fun ImageButton.bindPin(pinned: Boolean) {
    setImageResource(
        if (pinned) R.drawable.baseline_push_pin_24 else R.drawable.outline_push_pin_24
    )
    imageTintList = ContextCompat.getColorStateList(
        context, if (pinned) R.color.icon_accent else R.color.buttonL3
    )
}

fun AppCompatActivity.handleDataStoreError(e: Exception) {
    val message = when (e) {
        is DataStoreException -> e.message ?: "Data error"
        is SecurityException -> "Permission denied — cannot access storage"
        is IOException -> "Storage read/write failed"
        else -> "Unexpected error: ${e.message}"
    }
    runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}