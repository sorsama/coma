package com.zyphite.coma

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyphite.coma.screens.AutomateScreen
import com.zyphite.coma.screens.ChatScreen
import com.zyphite.coma.screens.ComposerSheet
import com.zyphite.coma.screens.ConnectScreen
import com.zyphite.coma.screens.GatewayScreen
import com.zyphite.coma.screens.HostsScreen
import com.zyphite.coma.screens.MindScreen
import com.zyphite.coma.screens.SystemScreen
import com.zyphite.coma.screens.ThreadsScreen
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.ComaTheme
import com.zyphite.coma.ui.EnterEase
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5

enum class Tab(val label: String) { Threads("Threads"), Automate("Automate"), Mind("Mind"), System("System") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Threads -> Icons.Threads
    Tab.Automate -> Icons.Clock
    Tab.Mind -> Icons.Mind
    Tab.System -> Icons.System
}

private const val CHAT = "chat"
private const val GATEWAY = "gateway"
private const val HOSTS = "hosts"
private const val CONNECT = "connect"

@Composable
fun ComaApp(vm: ComaViewModel = viewModel()) {
    ComaTheme {
        var tab by rememberSaveable { mutableStateOf(Tab.Threads) }
        val stack = rememberSaveable(
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<String>() }
        // The composer sheet: null while closed, otherwise the section it opens on.
        var composer by rememberSaveable { mutableStateOf<Int?>(null) }

        val route = if (vm.hosts.isEmpty()) CONNECT else stack.lastOrNull() ?: "tab:${tab.name}"
        val back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
        val toTab = { target: Tab -> stack.clear(); tab = target }

        BackHandler(stack.isNotEmpty() && vm.hosts.isNotEmpty()) { back() }
        BackHandler(stack.isEmpty() && tab != Tab.Threads && vm.hosts.isNotEmpty()) { tab = Tab.Threads }

        Box(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInHorizontally(tween(280, easing = EnterEase)) { 30 }) togetherWith
                            fadeOut(tween(100))
                    },
                    label = "route",
                ) { current ->
                    when (current) {
                        CONNECT -> ConnectScreen(vm, onBack = if (vm.hosts.isEmpty()) null else back, onDone = { stack.remove(CONNECT) })
                        CHAT -> ChatScreen(vm, onBack = back, onComposer = { composer = 0 })
                        GATEWAY -> GatewayScreen(vm, onBack = back)
                        HOSTS -> HostsScreen(vm, onBack = back, onConnect = { stack.add(CONNECT) })
                        "tab:Automate" -> AutomateScreen(vm, onAsk = { text -> vm.newThread(); vm.send(text); stack.add(CHAT) })
                        "tab:Mind" -> MindScreen(vm)
                        "tab:System" -> SystemScreen(
                            vm, onHosts = { stack.add(HOSTS) }, onGateway = { stack.add(GATEWAY) }, onModel = { composer = 1 },
                        )
                        else -> ThreadsScreen(
                            vm,
                            onOpen = { session -> vm.open(session); stack.add(CHAT) },
                            onCompose = { composer = 0 },
                            onSay = { text -> vm.newThread(); vm.send(text); stack.add(CHAT) },
                            onSystem = { toTab(Tab.System) },
                        )
                    }
                }
                if (route.startsWith("tab:") || route == GATEWAY) {
                    TabBar(if (route == GATEWAY) Tab.System else tab, toTab)
                }
            }
            ComposerSheet(
                vm = vm,
                section = composer,
                inChat = route == CHAT,
                onClose = { composer = null },
                onSend = { text ->
                    composer = null
                    if (route != CHAT) vm.newThread()
                    vm.send(text)
                    if (route != CHAT) stack.add(CHAT)
                },
                onNew = { composer = null; vm.newThread(); if (route != CHAT) stack.add(CHAT) },
                onSkills = { composer = null; toTab(Tab.Mind) },
            )
        }
    }
}

/**
 * The floating tab bar: a glass capsule with a pill that marks the open tab. Press and the bar swells
 * while the pill lifts under the finger; hold and drag to carry it across; let go and it springs into
 * the nearest tab, which opens.
 */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val tabs = Tab.entries
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    // Where the pill sits, in tab widths from the first tab.
    val position = remember { Animatable(tabs.indexOf(current).toFloat()) }
    var held by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(tabs.indexOf(current)) }
    val lift by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = .55f, stiffness = 420f), label = "lift")

    LaunchedEffect(current) {
        if (!held) position.animateTo(tabs.indexOf(current).toFloat(), spring(dampingRatio = .7f, stiffness = 380f))
    }

    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, top = 6.dp, end = 24.dp, bottom = 10.dp),
        Alignment.Center,
    ) {
        Box(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().height(64.dp)
                .graphicsLayer {
                    val swell = 1f + .035f * lift
                    scaleX = swell
                    scaleY = swell
                }
                .shadow(20.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(C.Raised, C.Surface2)))
                .border(1.dp, Brush.verticalGradient(listOf(C.BorderStrong, C.Border)), CircleShape)
                .padding(5.dp)
                .pointerInput(tabs.size) {
                    val at = { x: Float -> (x / size.width * tabs.size - .5f).coerceIn(0f, tabs.size - 1f) }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var x = down.position.x
                        held = true
                        hover = at(x).roundToInt()
                        var glide = scope.launch { position.animateTo(at(x), spring(dampingRatio = .75f, stiffness = 520f)) }
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position.x != x) {
                                x = change.position.x
                                change.consume()
                                glide.cancel()
                                glide = scope.launch { position.snapTo(at(x)) }
                                val over = at(x).roundToInt()
                                if (over != hover) {
                                    hover = over
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        held = false
                        val target = at(x).roundToInt()
                        select(tabs[target])
                        glide.cancel()
                        scope.launch { position.animateTo(target.toFloat(), spring(dampingRatio = .5f, stiffness = 320f)) }
                    }
                },
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(1f / tabs.size)
                    .graphicsLayer {
                        translationX = position.value * size.width
                        val grow = 1f + .14f * lift
                        scaleX = grow
                        scaleY = grow
                        alpha = .55f + .45f * lift
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(C.Text.copy(alpha = .22f), C.Text.copy(alpha = .10f))))
                    .border(1.dp, C.Text.copy(alpha = .18f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val on = if (held) index == hover else tab == current
                    val color by animateColorAsState(if (on) C.Text else C.Dim, tween(180), label = "tab")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = tab == current
                                onClick { select(tab); true }
                            }
                            .graphicsLayer {
                                val pop = 1f + (if (on) .1f else 0f) * lift
                                scaleX = pop
                                scaleY = pop
                            },
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Ico(tab.icon(), 22.dp, color)
                        T(tab.label, size = 11.sp, weight = W5, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}
