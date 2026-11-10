package dev.devforge

import android.content.Context
import android.content.res.AssetManager
import dev.devforge.core.data.ContentFiles
import dev.devforge.core.data.FileProgressStore
import dev.devforge.core.data.YamlContentLoader
import dev.devforge.core.domain.ProgressStore
import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** État du chargement du contenu. */
sealed interface ContentState {
    data object Loading : ContentState
    data class Ready(val content: Content) : ContentState
    data class Failed(val message: String) : ContentState
}

/** Adaptateur : lit le contenu dans les assets de l'APK. */
class AssetContentFiles(private val assets: AssetManager) : ContentFiles {
    override fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
    override fun list(dir: String): List<String> = assets.list(dir)?.sorted().orEmpty()
}

/**
 * La progression, observable par l'interface. Chaque modification est enregistrée
 * sur le disque, dans l'ordre, par un seul collecteur.
 */
class ProgressRepository(private val store: ProgressStore, scope: CoroutineScope) {
    private val state = MutableStateFlow(Progress())
    val flow: StateFlow<Progress> = state.asStateFlow()
    val value: Progress get() = state.value

    init {
        scope.launch(Dispatchers.IO) {
            state.drop(1).collect { store.save(it) }
        }
    }

    fun loadFromDisk() {
        state.value = store.load()
    }

    fun set(progress: Progress) {
        state.value = progress
    }

    fun update(transform: (Progress) -> Progress) = state.update(transform)
}

/** Injection de dépendances faite à la main : tout l'assemblage de l'app est ici. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loader = YamlContentLoader(AssetContentFiles(context.assets))

    val progress = ProgressRepository(FileProgressStore(File(context.filesDir, "progress.json")), scope)

    private val contentState = MutableStateFlow<ContentState>(ContentState.Loading)
    val content: StateFlow<ContentState> = contentState.asStateFlow()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun load() {
        contentState.value = ContentState.Loading
        scope.launch(Dispatchers.IO) {
            contentState.value = try {
                progress.loadFromDisk()
                ContentState.Ready(loader.load())
            } catch (e: Exception) {
                ContentState.Failed(e.message ?: e.toString())
            }
        }
    }
}
