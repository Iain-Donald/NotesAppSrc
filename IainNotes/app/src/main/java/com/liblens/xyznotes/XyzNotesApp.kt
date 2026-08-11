package com.liblens.xyznotes

import android.app.Application

class XyzNotesApp : Application() {
	override fun onCreate() {
		super.onCreate()
		BlobStore.init(this)      // must precede anything that touches storage
		//ThemeManager.apply()
	}
}