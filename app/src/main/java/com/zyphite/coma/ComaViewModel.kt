package com.zyphite.coma

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyphite.coma.billing.BillingManager
import com.zyphite.coma.data.Approval
import com.zyphite.coma.data.ChatItem
import com.zyphite.coma.data.Discovery
import com.zyphite.coma.data.Found
import com.zyphite.coma.data.Health
import com.zyphite.coma.data.Hermes
import com.zyphite.coma.data.HermesException
import com.zyphite.coma.data.Host
import com.zyphite.coma.data.Job
import com.zyphite.coma.data.ModelChoice
import com.zyphite.coma.data.ModelOptions
import com.zyphite.coma.data.Session
import com.zyphite.coma.data.Skill
import com.zyphite.coma.data.StepState
import com.zyphite.coma.data.Toolset
import com.zyphite.coma.data.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class RunState { Idle, Thinking, Tool, Wait }

/** One thread as the phone sees it. It outlives the chat screen, so a run keeps streaming while you look elsewhere. */
class Conversation(sessionId: String?, title: String, val source: String) {
    /** Null until the first message creates the session on the host. */
    var sessionId by mutableStateOf(sessionId)
    var title by mutableStateOf(title)
    val items = mutableStateListOf<ChatItem>()
    var state by mutableStateOf(RunState.Idle)
    var approval by mutableStateOf<Approval?>(null)
    var loading by mutableStateOf(false)
    var runId: String? = null

    /** The assistant message the current deltas append to; cleared whenever a tool call interrupts the text. */
    var streamId: String? = null
}

/** App state: the hosts and the plan (persisted), and what the active host reports over the Hermes API. */
class ComaViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("coma", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 0

    val billing = BillingManager(app, prefs).also { it.connect() }

    val hosts = mutableStateListOf<Host>().apply { addAll(loadHosts()) }
    var activeHost by mutableIntStateOf(prefs.getInt("active", 0))
        private set

    /** Whether the active host answered its last request; null before the first answer. */
    var online by mutableStateOf<Boolean?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set

    /** Machines on this network that answered the last search. */
    val found = mutableStateListOf<Found>()
    var searching by mutableStateOf(false)
        private set

    val sessions = mutableStateListOf<Session>()
    var sessionsError by mutableStateOf<String?>(null)
        private set
    var sessionsLoaded by mutableStateOf(false)
        private set
    private val conversations = mutableStateMapOf<String, Conversation>()
    var current by mutableStateOf<Conversation?>(null)
        private set

    val jobs = mutableStateListOf<Job>()
    var jobsError by mutableStateOf<String?>(null)
        private set
    val skills = mutableStateListOf<Skill>()
    var skillsError by mutableStateOf<String?>(null)
        private set
    val toolsets = mutableStateListOf<Toolset>()
    var toolsetsError by mutableStateOf<String?>(null)
        private set
    var health by mutableStateOf<Health?>(null)
        private set
    var modelOptions by mutableStateOf<ModelOptions?>(null)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    /** The model new turns ask for; null leaves the choice to the host's own default. */
    var model by mutableStateOf<ModelChoice?>(null)
        private set

    val host: Host? get() = hosts.getOrNull(activeHost) ?: hosts.firstOrNull()
    val canAddHost: Boolean get() = billing.isPlus || hosts.size < FREE_HOSTS

    /** Threads that are running or waiting on you, as far as this phone has seen. */
    val busy: List<Conversation> get() = conversations.values.filter { it.state != RunState.Idle }

    fun conversation(sessionId: String): Conversation? = conversations[sessionId]

    fun pickHost(index: Int) {
        if (index == activeHost) return
        activeHost = index
        prefs.edit().putInt("active", index).apply()
        forgetHostState()
        refreshSessions()
    }

    fun pickModel(choice: ModelChoice?) { model = choice }

    /**
     * Adds the host at [address] once it has proved to be a Hermes API server that accepts [key].
     * A pairing code carries the host's [name]; pairing a name this phone already has updates that host,
     * because a tunnel's address changes every time it is opened.
     * [onResult] gets an error sentence, or null when the host was saved and made active.
     */
    fun connect(address: String, key: String, name: String? = null, onResult: (String?) -> Unit) {
        val url = Hermes.address(address) ?: return onResult("Type the host's address, like 192.168.1.20:8642.")
        if (key.isBlank()) return onResult("Type the API key from the host's .env file.")
        val label = name?.trim()?.take(40)?.ifBlank { null }
        val replaces = if (label == null) -1 else hosts.indexOfFirst { it.name == label }
        if (replaces < 0) {
            if (hosts.any { it.url == url }) return onResult("That host is already on this phone.")
            if (!canAddHost) return onResult("The free plan covers one host. Plus adds as many as you like.")
        }
        val authority = url.substringAfter("://").substringBefore("/")
        val bare = authority.substringBefore(":")
        val candidate = Host(label ?: if (hosts.any { it.name == bare }) authority else bare, url, key.trim())
        connecting = true
        viewModelScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { Hermes(candidate).verify() }
                null
            } catch (failure: HermesException) {
                failure.message
            }
            connecting = false
            if (error == null) {
                if (replaces in hosts.indices) hosts[replaces] = candidate else hosts.add(candidate)
                saveHosts()
                activeHost = hosts.indexOf(candidate)
                prefs.edit().putInt("active", activeHost).apply()
                forgetHostState()
                online = true
                refreshSessions()
            }
            onResult(error)
        }
    }

    /** Looks for hosts on this network that have pairing started. */
    fun search() {
        if (searching) return
        searching = true
        // An emulator sits behind its own NAT, where a broadcast never reaches the computer it runs on.
        val debuggable = getApplication<Application>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        viewModelScope.launch {
            val answers = withContext(Dispatchers.IO) { Discovery.search(if (debuggable) listOf("10.0.2.2") else emptyList()) }
            found.clear()
            found.addAll(answers)
            searching = false
        }
    }

    /** Pairs with a machine found on this network, using the six-digit [code] it shows. */
    fun pairWith(host: Found, code: String, onResult: (String?) -> Unit) {
        if (code.length != 6) return onResult("Type the six digits ${host.name} is showing.")
        if (connecting) return
        connecting = true
        viewModelScope.launch {
            val pairing = try {
                withContext(Dispatchers.IO) { Discovery.pair(host, code) }
            } catch (failure: HermesException) {
                connecting = false
                return@launch onResult(failure.message)
            }
            connecting = false
            connect(pairing.url, pairing.key, pairing.name, onResult)
        }
    }

    fun removeHost(index: Int) {
        if (index !in hosts.indices) return
        val wasActive = index == activeHost
        hosts.removeAt(index)
        saveHosts()
        if (wasActive || activeHost > hosts.lastIndex) {
            activeHost = 0
            forgetHostState()
            refreshSessions()
        } else if (index < activeHost) {
            activeHost -= 1
        }
        prefs.edit().putInt("active", activeHost).apply()
    }

    fun refreshSessions() = call({ it.sessions() }, { sessionsError = it }) { list ->
        sessions.clear()
        sessions.addAll(list)
        sessionsLoaded = true
        sessionsError = null
        list.forEach { session -> conversations[session.id]?.title = session.title }
    }

    fun refreshJobs() = call({ it.jobs() }, { jobsError = it }) { jobs.clear(); jobs.addAll(it); jobsError = null }

    fun refreshSkills() = call({ it.skills() }, { skillsError = it }, tracksReach = false) {
        skills.clear(); skills.addAll(it); skillsError = null
    }

    fun refreshToolsets() = call({ it.toolsets() }, { toolsetsError = it }, tracksReach = false) {
        toolsets.clear(); toolsets.addAll(it); toolsetsError = null
    }

    fun refreshHealth() = call({ it.health() }, { health = null }) { health = it }

    fun refreshModels() = call({ it.modelOptions() }, { modelsError = it }, tracksReach = false) {
        modelOptions = it; modelsError = null
    }

    fun setJobEnabled(job: Job, enabled: Boolean) {
        val at = jobs.indexOf(job)
        if (at >= 0) jobs[at] = job.copy(enabled = enabled)
        call({ it.setJobEnabled(job.id, enabled) }, { jobsError = it; refreshJobs() }) { updated ->
            val now = jobs.indexOfFirst { it.id == updated.id }
            if (now >= 0) jobs[now] = updated
        }
    }

    /** Opens a thread from the list, loading its transcript unless a run is already feeding it. */
    fun open(session: Session) {
        val conversation = conversations.getOrPut(session.id) { Conversation(session.id, session.title, session.source) }
        current = conversation
        if (conversation.state != RunState.Idle) return
        conversation.loading = conversation.items.isEmpty()
        call({ it.messages(session.id) }, { conversation.loading = false; conversation.note(it) }) { items ->
            conversation.loading = false
            if (conversation.state == RunState.Idle) {
                conversation.items.clear()
                conversation.items.addAll(items)
            }
        }
    }

    fun newThread() {
        current = Conversation(null, "New thread", "api_server")
    }

    /** Sends [text] to the open thread, starting a new one when none is open, and streams the turn back. */
    fun send(text: String) {
        val message = text.trim()
        val api = api() ?: return
        if (message.isEmpty()) return
        val conversation = current ?: Conversation(null, "New thread", "api_server").also { current = it }
        if (conversation.state != RunState.Idle) return
        if (conversation.items.isEmpty() && conversation.sessionId == null) conversation.title = message.take(60)
        conversation.items += ChatItem.User(id(), message)
        conversation.state = RunState.Thinking
        conversation.streamId = null
        val picked = model
        viewModelScope.launch(Dispatchers.IO) {
            val failure = try {
                val sessionId = conversation.sessionId ?: api.createSession().id.also { created ->
                    main.post { conversation.sessionId = created; conversations[created] = conversation }
                }
                api.chat(sessionId, message, picked) { name, event -> main.post { onEvent(conversation, name, event) } }
                null
            } catch (error: HermesException) {
                error.message
            }
            main.post {
                if (failure != null) conversation.note(failure)
                conversation.items.forEachIndexed { index, item ->
                    if (item is ChatItem.Step && item.state == StepState.Running) {
                        conversation.items[index] = item.copy(state = StepState.Failed)
                    }
                }
                conversation.state = RunState.Idle
                conversation.approval = null
                conversation.runId = null
                refreshSessions()
            }
        }
    }

    /** Answers the pending approval with once, session, always or deny. */
    fun answer(conversation: Conversation, choice: String) {
        val approval = conversation.approval ?: return
        conversation.approval = null
        conversation.state = RunState.Tool
        call({ it.approve(approval, choice) }, { conversation.note(it) }) {}
    }

    fun stop(conversation: Conversation) {
        val runId = conversation.runId ?: return
        call({ it.stop(runId) }, { conversation.note(it) }) {}
    }

    private fun onEvent(conversation: Conversation, name: String, event: JSONObject) {
        val items = conversation.items
        when (name) {
            "run.started" -> conversation.runId = event.optString("run_id")
            "assistant.delta" -> {
                val delta = event.optString("delta")
                val last = items.lastOrNull()
                if (last is ChatItem.Assistant && last.id == conversation.streamId) {
                    items[items.lastIndex] = last.copy(text = last.text + delta)
                } else if (delta.isNotBlank()) {
                    items += ChatItem.Assistant(id().also { conversation.streamId = it }, delta.trimStart())
                }
                if (conversation.state != RunState.Wait) conversation.state = RunState.Thinking
            }
            "tool.started" -> {
                conversation.streamId = null
                items += ChatItem.Step(
                    id(), event.optString("tool_name"), event.optString("preview").takeUnless { event.isNull("preview") }.orEmpty(),
                    StepState.Running,
                )
                conversation.state = RunState.Tool
            }
            "tool.completed", "tool.failed" -> {
                val at = items.indexOfLast { it is ChatItem.Step && it.state == StepState.Running && it.tool == event.optString("tool_name") }
                (items.getOrNull(at) as? ChatItem.Step)?.let {
                    items[at] = it.copy(state = if (name == "tool.failed") StepState.Failed else StepState.Done)
                }
                conversation.approval = null
                conversation.state = RunState.Thinking
            }
            "approval.request" -> {
                conversation.approval = Hermes.approval(event)
                conversation.state = RunState.Wait
            }
            "assistant.completed" -> {
                val content = event.optString("content").trim()
                val streamed = (items.lastOrNull() as? ChatItem.Assistant)?.takeIf { it.id == conversation.streamId }
                if (streamed != null) {
                    if (content.isNotEmpty()) items[items.lastIndex] = streamed.copy(text = content)
                } else if (content.isNotEmpty()) {
                    items += ChatItem.Assistant(id(), content)
                }
            }
            "run.cancelled" -> conversation.note("Stopped.")
            "run.failed" -> if (items.lastOrNull() !is ChatItem.Assistant) conversation.note("Hermes couldn't finish this turn.")
            "run.queued" -> conversation.note("Queued. The chat's owner on the host will answer it.")
            "error" -> conversation.note(event.optString("message").ifBlank { "Hermes reported an error." })
        }
    }

    private fun Conversation.note(text: String) {
        items += ChatItem.Notice(id(), text)
    }

    private fun id(): String = "local-${nextId++}"

    private fun api(): Hermes? = host?.let(::Hermes)

    /** Runs [block] against the active host off the main thread; the answer is dropped if the host changed meanwhile. */
    private fun <T> call(block: (Hermes) -> T, onError: (String) -> Unit, tracksReach: Boolean = true, onResult: (T) -> Unit) {
        val asked = host ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { block(Hermes(asked)) }
                if (host != asked) return@launch
                if (tracksReach) online = true
                onResult(result)
            } catch (failure: HermesException) {
                if (host != asked) return@launch
                // Status 0 means the host never answered; any HTTP answer means it is there.
                if (tracksReach) online = failure.status != 0
                onError(failure.message.orEmpty())
            }
        }
    }

    private fun forgetHostState() {
        online = null
        sessions.clear(); sessionsLoaded = false; sessionsError = null
        conversations.clear(); current = null
        jobs.clear(); jobsError = null
        skills.clear(); skillsError = null
        toolsets.clear(); toolsetsError = null
        health = null; modelOptions = null; modelsError = null; model = null
    }

    private fun loadHosts(): List<Host> = runCatching {
        val array = JSONArray(prefs.getString("hosts", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            // A key that no longer opens (a restored backup, a cleared keystore) drops its host: re-add it.
            Vault.open(item.getString("key"))?.let { Host(item.getString("name"), item.getString("url"), it) }
        }
    }.getOrDefault(emptyList())

    private fun saveHosts() {
        val array = JSONArray()
        hosts.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url).put("key", Vault.seal(it.key))) }
        prefs.edit().putString("hosts", array.toString()).apply()
    }

    companion object {
        const val FREE_HOSTS = 1
    }
}
