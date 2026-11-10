package dev.devforge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.devforge.AppContainer
import dev.devforge.ContentState
import dev.devforge.core.domain.session.SessionKind
import dev.devforge.ui.common.ErrorMessage
import dev.devforge.ui.common.Loading
import dev.devforge.ui.home.HomeScreen
import dev.devforge.ui.lesson.LessonScreen
import dev.devforge.ui.profile.ProfileScreen
import dev.devforge.ui.session.SessionRequest
import dev.devforge.ui.session.SessionScreen
import dev.devforge.ui.track.TrackScreen

/** Les routes de navigation de l'app. */
object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val TRACK = "track/{trackId}"
    const val LESSON = "lesson/{lessonId}"
    const val SESSION = "session/{kind}?track={track}&level={level}&lesson={lesson}"

    fun track(id: String) = "track/$id"
    fun lesson(id: String) = "lesson/$id"
    fun session(kind: SessionKind, track: String? = null, level: Int? = null, lesson: String? = null) =
        "session/${kind.name}?track=${track.orEmpty()}&level=${level ?: 0}&lesson=${lesson.orEmpty()}"
}

@Composable
fun DevForgeApp(container: AppContainer) {
    val contentState by container.content.collectAsStateWithLifecycle()
    val progress by container.progress.flow.collectAsStateWithLifecycle()

    val state = contentState
    if (state is ContentState.Loading) {
        Loading("Chargement des parcours…")
        return
    }
    if (state is ContentState.Failed) {
        ErrorMessage(state.message) { container.load() }
        return
    }
    val content = (state as ContentState.Ready).content
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                content = content,
                progress = progress,
                today = container.today(),
                onOpenTrack = { nav.navigate(Routes.track(it)) },
                onOpenProfile = { nav.navigate(Routes.PROFILE) },
                onStartSession = { kind -> nav.navigate(Routes.session(kind)) },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                content = content,
                progress = progress,
                today = container.today(),
                onBack = { nav.popBackStack() },
                onReset = { container.progress.set(dev.devforge.core.model.Progress()) },
            )
        }
        composable(Routes.TRACK, arguments = listOf(navArgument("trackId") { type = NavType.StringType })) { entry ->
            TrackScreen(
                content = content,
                progress = progress,
                trackId = entry.arguments?.getString("trackId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenLesson = { nav.navigate(Routes.lesson(it)) },
                onOpenTrack = { nav.navigate(Routes.track(it)) },
                onTrain = { track, level -> nav.navigate(Routes.session(SessionKind.LEVEL, track, level)) },
                onBoss = { track, level -> nav.navigate(Routes.session(SessionKind.BOSS, track, level)) },
            )
        }
        composable(Routes.LESSON, arguments = listOf(navArgument("lessonId") { type = NavType.StringType })) { entry ->
            val lessonId = entry.arguments?.getString("lessonId").orEmpty()
            LessonScreen(
                content = content,
                progress = progress,
                lessonId = lessonId,
                onBack = { nav.popBackStack() },
                onRead = { container.progress.update { it.copy(lessonsRead = it.lessonsRead + lessonId) } },
                onPractice = {
                    nav.navigate(Routes.session(SessionKind.LESSON, lesson = lessonId)) {
                        popUpTo(Routes.LESSON) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.SESSION,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("track") { type = NavType.StringType; defaultValue = "" },
                navArgument("level") { type = NavType.IntType; defaultValue = 0 },
                navArgument("lesson") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments
            val request = SessionRequest(
                kind = SessionKind.valueOf(args?.getString("kind") ?: SessionKind.DAILY.name),
                trackId = args?.getString("track")?.ifEmpty { null },
                level = args?.getInt("level")?.takeIf { it > 0 },
                lessonId = args?.getString("lesson")?.ifEmpty { null },
            )
            SessionScreen(
                container = container,
                request = request,
                routeKey = entry.id,
                onClose = { nav.popBackStack() },
            )
        }
    }
}
