package com.zyphite.coma.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.RunState
import com.zyphite.coma.data.Session
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Chip
import com.zyphite.coma.ui.Field
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.IconBtn
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.ScreenHeader
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.Wing
import com.zyphite.coma.ui.bottomLine
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.enter
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.rowPress
import com.zyphite.coma.ui.topLine
import kotlinx.coroutines.delay

/** How a session's source reads in the list: where the thread was started. */
fun sourceLabel(source: String): String = when (source) {
    "api_server" -> "API"
    "cli", "tui", "acp" -> source.uppercase()
    else -> source.replace('_', ' ').replaceFirstChar(Char::uppercase)
}

/** How long ago [seconds] (Unix time) was, in the list's short form. */
private fun age(seconds: Double): String {
    val minutes = ((System.currentTimeMillis() / 1000.0 - seconds) / 60).toLong()
    return when {
        seconds <= 0 -> ""
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}

@Composable
fun ThreadsScreen(
    vm: ComaViewModel,
    onOpen: (Session) -> Unit,
    onCompose: () -> Unit,
    onSay: (String) -> Unit,
    onSystem: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val dictate = rememberDictation(onSay)

    // The list is the host's, so keep asking while it is on screen.
    LaunchedEffect(vm.host) {
        while (true) {
            vm.refreshSessions()
            delay(15_000)
        }
    }

    val state = { session: Session -> vm.conversation(session.id)?.state ?: RunState.Idle }
    val filters = listOf("all" to "All", "live" to "Live") + vm.sessions.map { it.source }.distinct().map { it to sourceLabel(it) }
    val threads = vm.sessions.filter {
        when (filter) {
            "all" -> true
            "live" -> state(it) != RunState.Idle
            else -> it.source == filter
        }
    }.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }

    val busy = vm.busy
    val (signal, signalLabel) = when {
        busy.any { it.state == RunState.Wait } -> Signal.Wait to "Hermes needs you"
        busy.isNotEmpty() -> Signal.Think to "Hermes is working"
        vm.online == false -> Signal.Off to "Host unreachable"
        else -> Signal.Idle to "Hermes is idle"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Threads", signal, signalLabel, endPad = 12.dp) {
            Row(
                Modifier.height(44.dp).press(onClick = onSystem).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (vm.online == false) C.Mute else C.Text))
                T(vm.host?.name.orEmpty(), Modifier.widthIn(max = 120.dp), size = 12.sp, color = C.Sub, mono = true, maxLines = 1)
            }
            IconBtn(Icons.Search, "Search threads", {
                searching = !searching
                if (!searching) query = ""
            }, dp = 20.dp, tint = if (searching) C.Text else C.Sub)
        }

        if (searching) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp).fillMaxWidth().height(44.dp)
                    .card(bg = C.Surface2).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Field(query, { query = it }, "Search threads", desc = "Search threads") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { (id, label) -> Chip(label, filter == id, { filter = id }) }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            threads.forEachIndexed { index, thread ->
                key(filter, thread.id) { ThreadRow(thread, state(thread), index) { onOpen(thread) } }
            }
            if (threads.isEmpty()) {
                val failure = vm.sessionsError
                T(
                    when {
                        failure != null -> failure
                        !vm.sessionsLoaded -> "Asking ${vm.host?.name.orEmpty()} for its threads."
                        vm.sessions.isEmpty() -> "No threads on this host yet. Say something to Hermes below."
                        else -> "Nothing here. Threads show up the moment they start."
                    },
                    Modifier.fillMaxWidth().padding(start = 40.dp, top = 48.dp, end = 40.dp, bottom = 12.dp),
                    color = C.Sub, align = TextAlign.Center,
                )
                if (failure != null) {
                    Box(Modifier.fillMaxWidth().height(44.dp).press(onClick = vm::refreshSessions), Alignment.Center) {
                        T("Try again", size = 14.sp, weight = W5, decoration = TextDecoration.Underline)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().topLine().padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).height(48.dp).press(onClick = onCompose).card(C.Surface2, C.Border, 24.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                T("/", color = C.Dim, mono = true)
                T("Message Hermes", color = C.Sub)
            }
            Box(Modifier.size(48.dp).press(onClick = dictate).clip(CircleShape).background(C.Text), Alignment.Center) {
                Ico(Icons.Voice, 22.dp, C.Bg, desc = "Talk to Hermes")
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: Session, state: RunState, index: Int, onOpen: () -> Unit) {
    val wait = state == RunState.Wait
    Row(
        Modifier.fillMaxWidth().enter(minOf(index, 8) * 45).rowPress(onClick = onOpen).bottomLine()
            .drawBehind {
                if (wait) {
                    drawRoundRect(
                        C.Amber, Offset(0f, 12.dp.toPx()), Size(2.dp.toPx(), size.height - 24.dp.toPx()),
                        CornerRadius(1.dp.toPx()),
                    )
                }
            }
            .heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(20.dp), Alignment.Center) {
            when (state) {
                RunState.Wait -> Wing(Signal.Wait, 14.dp, label = "Needs you")
                RunState.Idle -> Ico(Icons.Check, 16.dp, C.Dim, desc = "Finished")
                else -> Wing(Signal.Run, 14.dp, label = "Running")
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(thread.title, Modifier.weight(1f).alignByBaseline(), weight = W5, maxLines = 1)
                T(age(thread.lastActive), Modifier.alignByBaseline(), size = 12.sp, color = C.Dim, mono = true)
            }
            if (thread.preview.isNotBlank() && thread.preview != thread.title) {
                T(thread.preview.lines().first(), size = 12.sp, color = C.Sub, mono = true, maxLines = 1)
            }
            T(
                listOfNotNull("needs you".takeIf { wait }, sourceLabel(thread.source), thread.model.takeIf { it.isNotBlank() })
                    .joinToString(" · ").uppercase(),
                size = 11.sp, color = if (wait) C.Amber else C.Dim, mono = true, spacing = .05.em, maxLines = 1,
            )
        }
    }
}
