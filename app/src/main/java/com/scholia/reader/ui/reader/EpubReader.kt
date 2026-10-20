package com.scholia.reader.ui.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.scholia.reader.data.Highlight
import com.scholia.reader.ui.theme.HighlightColor
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

private const val HIGHLIGHTS = "highlights"
private const val TAG = "epub-navigator"

@OptIn(ExperimentalReadiumApi::class)
private fun preferences(dark: Boolean) = EpubPreferences(
    scroll = true,
    theme = if (dark) Theme.DARK else Theme.LIGHT,
    // Match the app surfaces instead of Readium's stock black/white.
    backgroundColor = ReadiumColor(if (dark) 0xFF141312.toInt() else 0xFFFDF9F1.toInt()),
    textColor = ReadiumColor(if (dark) 0xFFE6E1DF.toInt() else 0xFF1C1C17.toInt()),
    publisherStyles = false,
    lineHeight = 1.6,
    pageMargins = 1.2,
)

/**
 * Hosts Readium's [EpubNavigatorFragment] in Compose. The fragment is added to a container view
 * once that view is attached, and removed when it leaves composition.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReader(
    publication: Publication,
    initialLocator: Locator?,
    highlights: List<Highlight>,
    onNavigator: (EpubNavigatorFragment?) -> Unit,
    onLocator: (Locator) -> Unit,
    onSelection: (Selection?) -> Unit,
    onHighlightTap: (Long) -> Unit,
    /** A tap on the text that wasn't a link, highlight or selection. */
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as FragmentActivity
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val latestOnSelection by rememberUpdatedState(onSelection)
    val latestOnTap by rememberUpdatedState(onHighlightTap)
    val latestOnPlainTap by rememberUpdatedState(onTap)
    val containerId = remember { View.generateViewId() }

    // Replaces the system text-selection menu: we keep the selection alive with an empty menu and
    // show our own toolbar, which reads the selection back from the navigator.
    val selectionCallback = remember {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                scope.launch { latestOnSelection(navigator?.currentSelection()) }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false

            override fun onDestroyActionMode(mode: ActionMode) {
                latestOnSelection(null)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                doOnAttach {
                    val fm = activity.supportFragmentManager
                    val fragment = EpubNavigatorFactory(publication)
                        .createFragmentFactory(
                            initialLocator = initialLocator,
                            initialPreferences = preferences(dark),
                            configuration = EpubNavigatorFragment.Configuration(
                                selectionActionModeCallback = selectionCallback,
                            ),
                        )
                        .instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name)
                        as EpubNavigatorFragment
                    fm.beginTransaction().replace(containerId, fragment, TAG).commitNow()
                    fragment.addDecorationListener(
                        HIGHLIGHTS,
                        object : DecorableNavigator.Listener {
                            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                event.decoration.id.toLongOrNull()?.let(latestOnTap)
                                return true
                            }
                        },
                    )
                    fragment.addInputListener(
                        object : InputListener {
                            override fun onTap(event: TapEvent): Boolean {
                                latestOnPlainTap()
                                return true
                            }
                        },
                    )
                    navigator = fragment
                    onNavigator(fragment)
                }
            }
        },
        onRelease = {
            val fm = activity.supportFragmentManager
            fm.findFragmentByTag(TAG)?.let {
                if (!fm.isStateSaved) fm.beginTransaction().remove(it).commitNowAllowingStateLoss()
            }
            navigator = null
            onNavigator(null)
        },
    )

    val nav = navigator
    if (nav != null) {
        LaunchedEffect(nav) { nav.currentLocator.collect(onLocator) }
        LaunchedEffect(nav, dark) { nav.submitPreferences(preferences(dark)) }
        LaunchedEffect(nav, highlights, dark) {
            nav.applyDecorations(
                highlights.mapNotNull { h ->
                    val locator = h.location.toLocator() ?: return@mapNotNull null
                    Decoration(
                        id = h.id.toString(),
                        locator = locator,
                        style = Decoration.Style.Highlight(tint = HighlightColor.fromKey(h.color).tint(dark)),
                    )
                },
                HIGHLIGHTS,
            )
        }
    }
    DisposableEffect(Unit) { onDispose { onNavigator(null) } }
}
