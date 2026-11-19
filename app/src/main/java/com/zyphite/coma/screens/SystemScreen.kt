package com.zyphite.coma.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.ChevronRow
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.ScreenHeader
import com.zyphite.coma.ui.SectionLabel
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.W6
import com.zyphite.coma.ui.bottomLine
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.topLine

/** The active host as it reports itself: reachability, readiness checks, model and messaging platforms. */
@Composable
fun SystemScreen(vm: ComaViewModel, onHosts: () -> Unit, onGateway: () -> Unit, onModel: () -> Unit) {
    val host = vm.host
    val health = vm.health
    val degraded = health?.checks.orEmpty().filter { it.second != "ok" }

    LaunchedEffect(host) {
        vm.refreshHealth()
        if (vm.modelOptions == null) vm.refreshModels()
    }

    val (signal, label) = when {
        vm.online == false -> Signal.Off to "Host unreachable"
        degraded.isNotEmpty() -> Signal.Wait to "The host needs attention"
        else -> Signal.Idle to "Hermes is idle"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("System", signal, label)

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().press(onClick = onHosts).card().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ico(Icons.Server, 24.dp, C.Sub)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(host?.name.orEmpty(), weight = W6, maxLines = 1)
                    T(
                        listOfNotNull(
                            when (vm.online) { true -> "online"; false -> "unreachable"; null -> "checking" },
                            health?.version?.takeIf { it.isNotBlank() }?.let { "hermes $it" },
                            if (vm.billing.isPlus) "plus" else "free plan",
                        ).joinToString(" · "),
                        size = 12.sp, color = C.Sub, mono = true, maxLines = 1,
                    )
                }
                T("Hosts", size = 13.sp, weight = W5, decoration = TextDecoration.Underline)
            }

            SectionLabel("Readiness", Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp))
            if (health == null) {
                T(
                    if (vm.online == false) "No answer from the host." else "Asking the host how it is doing.",
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp), size = 14.sp, color = C.Sub,
                )
            }
            health?.checks?.forEach { (name, status) ->
                Row(
                    Modifier.fillMaxWidth().topLine().heightIn(min = 48.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(name.replace('_', ' ').replaceFirstChar(Char::uppercase), Modifier.weight(1f))
                    T(status, size = 12.sp, color = if (status == "ok") C.Sub else C.Amber, mono = true)
                }
            }

            SectionLabel(
                "Settings",
                Modifier.fillMaxWidth().topLine().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
            )
            ChevronRow("Model", (vm.model ?: vm.modelOptions?.current)?.model ?: "host default", onClick = onModel)
            ChevronRow(
                "Gateway", health?.let { h -> "${h.platforms.count { it.second == "connected" }} connected" },
                modifier = Modifier.bottomLine(), onClick = onGateway,
            )
            T(
                "Approval mode, the command backend, memory and secrets are set on the host itself with hermes config.",
                Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp), size = 13.sp, color = C.Dim,
            )
        }
    }
}
