package com.scholia.reader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.scholia.reader.ui.home.HomeScreen
import com.scholia.reader.ui.notes.NotesScreen
import com.scholia.reader.ui.reader.ReaderScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute

/** [highlightId] > 0 opens the book at that highlight instead of the last position. */
@Serializable data class ReaderRoute(val bookId: Long, val highlightId: Long = 0)

@Serializable data class NotesRoute(val bookId: Long)

@Composable
fun FolioNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenBook = { nav.navigate(ReaderRoute(it)) },
                onOpenNotes = { nav.navigate(NotesRoute(it)) },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderScreen(
                bookId = route.bookId,
                jumpToHighlight = route.highlightId,
                onBack = { nav.popBackStack() },
                onOpenNotes = { nav.navigate(NotesRoute(route.bookId)) },
            )
        }
        composable<NotesRoute> { entry ->
            val route = entry.toRoute<NotesRoute>()
            NotesScreen(
                bookId = route.bookId,
                onBack = { nav.popBackStack() },
                onJump = { highlightId ->
                    // Replace any reader already on the stack so Back returns to where we came from.
                    nav.navigate(ReaderRoute(route.bookId, highlightId)) {
                        popUpTo<ReaderRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
