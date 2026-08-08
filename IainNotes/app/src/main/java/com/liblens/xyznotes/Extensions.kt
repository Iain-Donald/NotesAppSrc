package com.liblens.xyznotes

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

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