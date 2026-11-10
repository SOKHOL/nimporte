package dev.devforge.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.devforge.AppContainer
import dev.devforge.ContentState
import dev.devforge.core.domain.evaluation.Answer
import dev.devforge.core.domain.session.SessionBuilder
import dev.devforge.core.domain.session.SessionKind
import dev.devforge.core.domain.session.SessionMachine
import dev.devforge.core.domain.session.SessionPlan
import dev.devforge.core.domain.session.SessionState
import dev.devforge.core.domain.session.Transition
import dev.devforge.core.model.Content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Ce que l'écran demande : quel type de session, sur quoi. */
data class SessionRequest(
    val kind: SessionKind,
    val trackId: String? = null,
    val level: Int? = null,
    val lessonId: String? = null,
)

/**
 * Relie la machine d'état du domaine à l'écran. Survit aux rotations ; la
 * progression est enregistrée après chaque réponse.
 */
class SessionViewModel(
    private val container: AppContainer,
    private val request: SessionRequest,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionState?>(null)
    val state: StateFlow<SessionState?> = _state.asStateFlow()

    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    private var machine: SessionMachine? = null

    init {
        viewModelScope.launch {
            val content = container.content.filterIsInstance<ContentState.Ready>().first().content
            val sessionMachine = SessionMachine(content)
            machine = sessionMachine
            val plan = plan(content)
            apply(sessionMachine.start(plan, container.progress.value))
            plan.timeLimitSeconds?.let(::startTimer)
        }
    }

    private fun plan(content: Content): SessionPlan {
        val builder = SessionBuilder(content)
        val progress = container.progress.value
        val today = container.today()
        val track = request.trackId.orEmpty()
        val level = request.level ?: 1
        return when (request.kind) {
            SessionKind.LEVEL -> builder.forLevel(track, level, progress)
            SessionKind.LESSON -> builder.forLesson(request.lessonId.orEmpty(), progress)
            SessionKind.REVIEW -> builder.forReview(progress, today)
            SessionKind.BOSS -> builder.forBoss(track, level)
            SessionKind.DAILY -> builder.daily(progress, today)
        }
    }

    fun submit(answer: Answer, hintsUsed: Int) {
        val current = _state.value as? SessionState.Answering ?: return
        val m = machine ?: return
        apply(m.submit(current, answer, hintsUsed, container.progress.value, container.today()))
    }

    fun next() {
        val current = _state.value as? SessionState.Feedback ?: return
        val m = machine ?: return
        apply(m.next(current, container.progress.value, container.today()))
    }

    private fun startTimer(seconds: Int) {
        _remainingSeconds.value = seconds
        viewModelScope.launch {
            while ((_remainingSeconds.value ?: 0) > 0 && _state.value !is SessionState.Finished) {
                delay(1_000)
                _remainingSeconds.value = (_remainingSeconds.value ?: 0) - 1
            }
            val current = _state.value
            val m = machine
            if (current != null && current !is SessionState.Finished && m != null) {
                apply(m.timeUp(current, container.progress.value, container.today()))
            }
        }
    }

    private fun apply(transition: Transition) {
        container.progress.set(transition.progress)
        _state.value = transition.state
    }
}
