package com.zyphite.coma.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A Hermes host this phone talks to: the address of its API server and the key that guards it. */
data class Host(val name: String, val url: String, val key: String)

data class Session(
    val id: String,
    val title: String,
    val preview: String,
    /** Where the thread started: api_server, cli, telegram, cron and so on. */
    val source: String,
    val model: String,
    val lastActive: Double,
)

enum class StepState { Running, Done, Failed }

sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val text: String) : ChatItem
    data class Assistant(override val id: String, val text: String) : ChatItem
    data class Step(override val id: String, val tool: String, val detail: String, val state: StepState) : ChatItem
    data class Notice(override val id: String, val text: String) : ChatItem
}

/** A command Hermes will not run until someone answers. The ids go back unchanged with the answer. */
data class Approval(
    val runId: String,
    val requestId: String,
    val generation: Int?,
    val command: String,
    val description: String,
    val choices: List<String>,
)

data class Job(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val nextRun: String,
    val lastStatus: String,
)

data class Skill(val name: String, val description: String, val category: String)

data class Toolset(val name: String, val label: String, val enabled: Boolean, val tools: Int)

data class ModelChoice(val provider: String, val providerName: String, val model: String)

data class ModelOptions(val current: ModelChoice?, val choices: List<ModelChoice>)

data class Health(
    val status: String,
    val version: String,
    /** Readiness checks by name, each "ok" or "degraded". */
    val checks: List<Pair<String, String>>,
    /** Messaging platforms the gateway serves, each with its connection state. */
    val platforms: List<Pair<String, String>>,
)

/** A failure with a sentence fit to show. [status] is the HTTP status, or 0 when the host never answered. */
class HermesException(val status: Int, message: String) : IOException(message)

/**
 * The Hermes API server (`hermes gateway` with `API_SERVER_ENABLED=true`), as documented at
 * hermes-agent.nousresearch.com/docs/user-guide/features/api-server. Every call blocks; run them off the main thread.
 */
class Hermes(private val host: Host) {

    /** Proves the address is a Hermes API server and the key is accepted. Returns the advertised model name. */
    fun verify(): String {
        val health = request("GET", "/health", auth = false)
        if (health.optString("platform") != "hermes-agent" && health.optString("status") != "ok") {
            throw HermesException(0, "That address answered, but not as a Hermes API server.")
        }
        return request("GET", "/v1/capabilities").optString("model", "hermes-agent")
    }

    fun sessions(): List<Session> =
        request("GET", "/api/sessions?limit=100").optJSONArray("data").objects().map(::session)

    fun createSession(): Session = session(request("POST", "/api/sessions", JSONObject()).getJSONObject("session"))

    fun deleteSession(id: String) {
        request("DELETE", "/api/sessions/$id")
    }

    fun messages(sessionId: String): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        request("GET", "/api/sessions/$sessionId/messages?inline_images=false").optJSONArray("data").objects()
            .forEachIndexed { index, message ->
                val id = message.optString("id").ifEmpty { "m$index" }
                val text = text(message.opt("content"))
                when (message.optString("role")) {
                    "user" -> if (text.isNotBlank()) items += ChatItem.User(id, text)
                    "assistant" -> {
                        message.optJSONArray("tool_calls").objects().forEach { call ->
                            val function = call.optJSONObject("function") ?: JSONObject()
                            items += ChatItem.Step(
                                call.optString("id").ifEmpty { "$id-call" }, function.optString("name"),
                                preview(function.optString("arguments")), StepState.Done,
                            )
                        }
                        if (text.isNotBlank()) items += ChatItem.Assistant(id, text)
                    }
                    "tool" -> if (failed(text)) {
                        val at = items.indexOfLast { it.id == message.optString("tool_call_id") }
                        (items.getOrNull(at) as? ChatItem.Step)?.let { items[at] = it.copy(state = StepState.Failed) }
                    }
                }
            }
        return items
    }

    /**
     * Runs one turn in [sessionId] and calls [onEvent] for every server-sent event until the turn ends.
     * Events: run.started, assistant.delta, tool.started, tool.completed, tool.failed, approval.request,
     * assistant.completed, run.completed, run.failed, run.cancelled, error, done.
     */
    fun chat(sessionId: String, message: String, model: ModelChoice?, onEvent: (String, JSONObject) -> Unit) {
        val body = JSONObject().put("message", message)
        if (model != null) body.put("model", model.model).put("provider", model.provider)
        val connection = open("POST", "/api/sessions/$sessionId/chat/stream", body, stream = true)
        try {
            if (connection.responseCode !in 200..299) throw failure(connection)
            connection.inputStream.bufferedReader().use { reader -> readEvents(reader, onEvent) }
        } catch (error: IOException) {
            throw friendly(error)
        } finally {
            connection.disconnect()
        }
    }

    fun approve(approval: Approval, choice: String) {
        val body = JSONObject().put("request_id", approval.requestId).put("choice", choice)
        if (approval.generation != null) body.put("execution_generation", approval.generation)
        request("POST", "/v1/runs/${approval.runId}/approval", body)
    }

    fun stop(runId: String) {
        request("POST", "/v1/runs/$runId/stop", JSONObject())
    }

    fun jobs(): List<Job> = request("GET", "/api/jobs?include_disabled=true").optJSONArray("jobs").objects().map(::job)

    fun createJob(name: String, schedule: String, prompt: String): Job = job(
        request("POST", "/api/jobs", JSONObject().put("name", name).put("schedule", schedule).put("prompt", prompt))
            .getJSONObject("job"),
    )

    fun setJobEnabled(id: String, enabled: Boolean): Job =
        job(request("POST", "/api/jobs/$id/" + if (enabled) "resume" else "pause", JSONObject()).getJSONObject("job"))

    fun runJob(id: String) {
        request("POST", "/api/jobs/$id/run", JSONObject())
    }

    fun deleteJob(id: String) {
        request("DELETE", "/api/jobs/$id")
    }

    fun skills(): List<Skill> = request("GET", "/v1/skills").optJSONArray("data").objects().map {
        Skill(it.optString("name"), it.optString("description"), it.optString("category"))
    }

    fun toolsets(): List<Toolset> = request("GET", "/v1/toolsets").optJSONArray("data").objects().map {
        Toolset(
            it.optString("name"), it.optString("label").ifEmpty { it.optString("name") },
            it.optBoolean("enabled"), it.optJSONArray("tools")?.length() ?: 0,
        )
    }

    /** The models of every provider the host is signed in to, and the one it uses by default. */
    fun modelOptions(): ModelOptions {
        val payload = request("GET", "/api/model/options?include_unconfigured=false", readTimeout = 60_000)
        val choices = payload.optJSONArray("providers").objects().filter { it.optBoolean("authenticated") }.flatMap { p ->
            val models = p.optJSONArray("models") ?: JSONArray()
            List(models.length()) { ModelChoice(p.optString("slug"), p.optString("name"), models.optString(it)) }
        }
        val current = choices.firstOrNull {
            it.provider == payload.optString("provider") && it.model == payload.optString("model")
        } ?: payload.optString("model").takeIf { it.isNotEmpty() }?.let {
            ModelChoice(payload.optString("provider"), payload.optString("provider"), it)
        }
        return ModelOptions(current, choices)
    }

    fun health(): Health {
        val payload = request("GET", "/health/detailed")
        val checks = payload.optJSONObject("readiness")?.optJSONObject("checks") ?: JSONObject()
        val platforms = payload.optJSONObject("platforms") ?: JSONObject()
        return Health(
            payload.optString("status", "ok"), payload.optString("version"),
            checks.keys().asSequence().map { it to (checks.optJSONObject(it)?.optString("status") ?: "") }.toList(),
            platforms.keys().asSequence().map { it to (platforms.optJSONObject(it)?.optString("state") ?: "") }.toList(),
        )
    }

    private fun session(item: JSONObject) = Session(
        id = item.optString("id"),
        title = item.str("title").ifBlank { item.str("preview") }.ifBlank { "New thread" },
        preview = item.str("preview"),
        source = item.str("source").ifBlank { "api_server" },
        model = item.str("model"),
        lastActive = item.optDouble("last_active").takeUnless { it.isNaN() } ?: item.optDouble("started_at", 0.0),
    )

    private fun job(item: JSONObject) = Job(
        id = item.optString("id"),
        name = item.str("name"),
        schedule = item.str("schedule_display").ifBlank { item.optJSONObject("schedule")?.optString("display").orEmpty() },
        enabled = item.optBoolean("enabled", true),
        nextRun = item.str("next_run_at"),
        lastStatus = item.str("last_status"),
    )

    private fun readEvents(reader: BufferedReader, onEvent: (String, JSONObject) -> Unit) {
        var event = ""
        val data = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        runCatching { JSONObject(data.toString()) }.getOrNull()?.let { onEvent(event, it) }
                    }
                    if (event == "done") return
                    event = ""
                    data.setLength(0)
                }
                line.startsWith(":") -> Unit // keepalive comment
                line.startsWith("event:") -> event = line.substring(6).trim()
                line.startsWith("data:") -> data.append(line.substring(5).trim())
            }
        }
    }

    private fun request(
        method: String, path: String, body: JSONObject? = null, auth: Boolean = true, readTimeout: Int = 20_000,
    ): JSONObject {
        val connection = open(method, path, body, auth = auth, readTimeout = readTimeout)
        try {
            if (connection.responseCode !in 200..299) throw failure(connection)
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return runCatching { JSONObject(text) }.getOrElse {
                throw HermesException(connection.responseCode, "That address answered, but not as a Hermes API server.")
            }
        } catch (error: IOException) {
            throw friendly(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        method: String, path: String, body: JSONObject?, auth: Boolean = true, stream: Boolean = false,
        readTimeout: Int = 20_000,
    ): HttpURLConnection {
        try {
            val connection = URL(host.url + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            // Streams send a keepalive every ten seconds, so a silent minute means the link is gone.
            connection.readTimeout = if (stream) 60_000 else readTimeout
            if (auth) connection.setRequestProperty("Authorization", "Bearer ${host.key}")
            connection.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            return connection
        } catch (error: IOException) {
            throw friendly(error)
        } catch (error: RuntimeException) {
            throw HermesException(0, "That doesn't look like a host address.")
        }
    }

    private fun failure(connection: HttpURLConnection): HermesException {
        val status = connection.responseCode
        val text = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val error = runCatching { JSONObject(text).opt("error") }.getOrNull()
        val said = when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
        return HermesException(
            status,
            when {
                status == 401 || status == 403 -> "The host refused that API key."
                status == 404 && said.isEmpty() -> "That address answered, but not as a Hermes API server."
                status == 429 -> "Hermes is at its limit of runs. Try again in a moment."
                said.isNotEmpty() -> said
                else -> "The host answered with an error ($status)."
            },
        )
    }

    private fun friendly(error: IOException): HermesException = when (error) {
        is HermesException -> error
        is UnknownHostException -> HermesException(0, "Can't find ${host.name}. Check the address.")
        is SocketTimeoutException -> HermesException(0, "${host.name} didn't answer in time.")
        is SSLException -> HermesException(0, "Couldn't make a secure connection to ${host.name}.")
        else -> HermesException(0, "Can't reach ${host.name}. Check the address and that hermes gateway is running.")
    }

    companion object {
        /** Reads an approval.request event. */
        fun approval(event: JSONObject): Approval {
            val choices = event.optJSONArray("choices") ?: JSONArray()
            return Approval(
                runId = event.optString("run_id"),
                requestId = event.optString("request_id").ifEmpty { event.optString("prompt_id") },
                generation = if (event.has("execution_generation")) event.optInt("execution_generation") else null,
                command = event.str("command"),
                description = event.str("description"),
                choices = List(choices.length()) { choices.optString(it) },
            )
        }

        /** The one line worth showing for a tool call: its command, path, query or url, else its arguments. */
        fun preview(arguments: String): String {
            val parsed = runCatching { JSONObject(arguments) }.getOrNull() ?: return arguments.take(160)
            val key = listOf("command", "path", "file_path", "query", "url", "prompt", "code").firstOrNull(parsed::has)
            return (if (key != null) parsed.optString(key) else arguments).replace('\n', ' ').take(160)
        }

        /** Builds a host from what was typed. The port defaults to the API server's own, 8642. */
        fun address(typed: String): String? {
            val trimmed = typed.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val url = runCatching { URL(withScheme) }.getOrNull() ?: return null
            if (url.host.isNullOrBlank() || url.protocol !in listOf("http", "https")) return null
            val port = if (url.port == -1 && url.protocol == "http") ":8642" else if (url.port == -1) "" else ":${url.port}"
            return "${url.protocol}://${url.host}$port${url.path.removeSuffix("/v1").trimEnd('/')}"
        }

        private fun text(content: Any?): String = when (content) {
            is String -> content
            is JSONArray -> content.objects().joinToString(" ") { part ->
                if (part.optString("type").contains("image")) "[image]" else part.optString("text")
            }
            else -> ""
        }.trim()

        private fun failed(result: String): Boolean {
            val parsed = runCatching { JSONObject(result) }.getOrNull() ?: return false
            return !parsed.isNull("error") || parsed.optInt("exit_code", 0) != 0
        }
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** optString, with JSON null read as empty and not as the word "null". */
private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)
