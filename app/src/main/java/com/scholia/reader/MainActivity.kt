package com.scholia.reader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.scholia.reader.ui.FolioNavHost
import com.scholia.reader.ui.theme.FolioTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // After process death Android restores the Readium fragment before we have a publication.
        // The dummy factory lets it be instantiated; we then drop it and Compose re-adds a real one.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        supportFragmentManager.fragments
            .filterIsInstance<EpubNavigatorFragment>()
            .takeIf { it.isNotEmpty() }
            ?.let { stale ->
                supportFragmentManager.beginTransaction()
                    .apply { stale.forEach(::remove) }
                    .commitNowAllowingStateLoss()
            }

        enableEdgeToEdge()
        setContent {
            FolioTheme {
                FolioNavHost()
            }
        }
    }
}
