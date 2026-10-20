package com.scholia.reader

import android.app.Application
import android.content.Context
import com.scholia.reader.data.AppDatabase
import com.scholia.reader.data.BookRepository
import com.scholia.reader.epub.EpubOpener

class ScholiaApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { BookRepository(database, contentResolver) }
    val epubOpener by lazy { EpubOpener(this) }
    val prefs by lazy { getSharedPreferences("scholia", MODE_PRIVATE) }
}

val Context.app: ScholiaApp get() = applicationContext as ScholiaApp
