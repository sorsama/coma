package com.zyphite.coma.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.ui.BackHeader
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Lane
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.Wing
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.topLine

private fun code(platform: String): String =
    platform.split('_', '-', ' ').filter { it.isNotEmpty() }.let { parts ->
        if (parts.size > 1) "${parts[0][0]}${parts[1][0]}" else platform.take(2)
    }.uppercase()

/** The messaging platforms the host's gateway serves, as the host reports them. They are set up on the host. */
@Composable
fun GatewayScreen(vm: ComaViewModel, onBack: () -> Unit) {
    LaunchedEffect(vm.host) { vm.refreshHealth() }
    val platforms = vm.health?.platforms.orEmpty()
    val live = platforms.count { it.second == "connected" }

    Column(Modifier.fillMaxSize()) {
        BackHeader("Gateway", "Back to System", onBack, trailing = "$live live")

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            if (platforms.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 6.dp).fillMaxWidth().card()
                        .padding(16.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        platforms.take(3).forEachIndexed { index, (name, state) ->
                            val on = state == "connected"
                            Row(
                                Modifier.height(28.dp).alpha(if (on) 1f else .45f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                T(code(name), Modifier.width(28.dp), size = 12.sp, color = C.Sub, mono = true)
                                Lane(
                                    Modifier.weight(1f), live = on, dashed = !on,
                                    color = if (on) C.Mute else C.BorderStrong, period = 2.2f, delay = index * .7f,
                                )
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(C.BorderStrong))
                    Wing(Signal.Idle, 30.dp, Modifier.padding(start = 4.dp, end = 6.dp), label = "Hermes")
                }
            }
            T(
                "Message Hermes from the apps you already use. Platforms are connected on the host with hermes gateway setup.",
                Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp), size = 13.sp, color = C.Sub,
            )

            platforms.forEach { (name, state) ->
                val on = state == "connected"
                Row(
                    Modifier.fillMaxWidth().topLine().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).alpha(if (on) 1f else .55f).clip(RoundedCornerShape(10.dp)).background(C.Chip),
                        Alignment.Center,
                    ) { T(code(name), size = 12.sp, mono = true) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(if (name == "api_server") "API server" else sourceLabel(name), weight = W5)
                        T(
                            if (name == "api_server") "$state · this app connects here" else state,
                            size = 12.sp, color = C.Sub, mono = true,
                        )
                    }
                }
            }
            if (vm.health == null) {
                T(
                    if (vm.online == false) "No answer from the host." else "Asking the host about its platforms.",
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp), size = 14.sp, color = C.Sub,
                )
            }
        }
    }
}
