package com.zyphite.coma.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.RunState
import com.zyphite.coma.data.Approval
import com.zyphite.coma.data.ChatItem
import com.zyphite.coma.data.StepState
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Field
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.IconBtn
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.LocalClock
import com.zyphite.coma.ui.LocalReduceMotion
import com.zyphite.coma.ui.MarchLine
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.SpringEase
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.W6
import com.zyphite.coma.ui.Wing
import com.zyphite.coma.ui.bottomLine
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.enter
import com.zyphite.coma.ui.frac
import com.zyphite.coma.ui.press
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One thread: its transcript from the host, the turn streaming in, and the approval Hermes may be waiting on. */
@Composable
fun ChatScreen(vm: ComaViewModel, onBack: () -> Unit, onComposer: () -> Unit) {
    val conversation = vm.current ?: return
    var reply by rememberSaveable { mutableStateOf("") }
    val host = vm.host?.name.orEmpty()
    val scroll = rememberScrollState()
    val send = { text: String -> if (conversation.state == RunState.Idle && text.isNotBlank()) { vm.send(text); reply = "" } }
    val dictate = rememberDictation { send(it) }

    // Stay with the newest line as the transcript loads and the reply streams in.
    LaunchedEffect(conversation) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }

    val running = conversation.items.lastOrNull { it is ChatItem.Step && it.state == StepState.Running } as? ChatItem.Step
    val (signal, signalLabel) = when (conversation.state) {
        RunState.Wait -> Signal.Wait to "Waiting for your approval"
        RunState.Tool -> Signal.Run to "Running a tool"
        RunState.Thinking -> Signal.Think to "Thinking"
        RunState.Idle -> Signal.Idle to "Idle"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().bottomLine().statusBarsPadding()
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconBtn(Icons.ChevronLeft, "Back to threads", onBack)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(conversation.title, size = 16.sp, weight = W6, spacing = (-0.01).em, maxLines = 1)
                    T(
                        listOfNotNull(sourceLabel(conversation.source).lowercase(), vm.model?.model, host).joinToString(" · "),
                        size = 12.sp, color = C.Dim, mono = true, maxLines = 1,
                    )
                }
                Box(Modifier.size(44.dp), Alignment.Center) { Wing(signal, 18.dp, label = signalLabel) }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (conversation.loading) T("Loading the thread from $host.", color = C.Sub)
                if (!conversation.loading && conversation.items.isEmpty()) {
                    T("Say what you need. Hermes does the work on $host and asks before anything risky.", color = C.Sub, lineHeight = 21.sp)
                }

                conversation.items.forEach { item ->
                    key(item.id) {
                        when (item) {
                            is ChatItem.User -> Bubble(item.text)
                            is ChatItem.Assistant -> T(item.text, lineHeight = 21.sp)
                            is ChatItem.Step -> Step(item)
                            is ChatItem.Notice -> T(item.text, size = 14.sp, color = C.Sub)
                        }
                    }
                }

                val approval = conversation.approval
                if (approval != null) {
                    key(approval.requestId) { Approval(approval, host) { choice -> vm.answer(conversation, choice) } }
                } else if (conversation.state != RunState.Idle) {
                    Column(Modifier.enter(dy = (-8).dp, ms = 380).card(C.Bg)) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Wing(if (running != null) Signal.Run else Signal.Think, 14.dp)
                            T(
                                if (running != null) "Running ${running.tool}" else "Hermes is thinking",
                                Modifier.weight(1f), weight = W5, maxLines = 1,
                            )
                            T("Stop", Modifier.press { vm.stop(conversation) }.padding(4.dp), size = 13.sp, color = C.Sub)
                        }
                        MarchLine()
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to C.Bg.copy(alpha = 0f), .42f to C.Bg, 1f to C.Bg))
                .navigationBarsPadding().padding(start = 12.dp, top = 36.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(48.dp).press(onClick = onComposer).shadow(10.dp, CircleShape).card(C.Raised, C.Border3, 24.dp)
                    .semantics { contentDescription = "Commands and model" },
                Alignment.Center,
            ) { T("/", size = 18.sp, color = C.Sub, mono = true) }
            Box(
                Modifier.weight(1f).height(48.dp).shadow(10.dp, CircleShape).card(C.Raised, C.Border3, 24.dp)
                    .padding(horizontal = 18.dp),
                Alignment.CenterStart,
            ) {
                Field(
                    reply, { reply = it },
                    if (conversation.state == RunState.Idle) "Reply to Hermes" else "Hermes is working",
                    keyboard = KeyboardOptions(imeAction = ImeAction.Send), desc = "Reply to Hermes",
                    onDone = { send(reply) },
                )
            }
            Box(
                Modifier.size(48.dp).press(onClick = dictate).shadow(10.dp, CircleShape).clip(CircleShape).background(C.Text),
                Alignment.Center,
            ) { Ico(Icons.Voice, 22.dp, C.Bg, desc = "Talk instead") }
        }
    }
}

@Composable
private fun Bubble(text: String) {
    Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
        T(
            text,
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp))
                .background(C.Hair).padding(horizontal = 14.dp, vertical = 10.dp),
            lineHeight = 21.sp,
        )
    }
}

/** One tool call: what Hermes used and on what. */
@Composable
private fun Step(step: ChatItem.Step) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(16.dp), Alignment.Center) {
            when (step.state) {
                StepState.Running -> Wing(Signal.Run, 11.dp, label = "Running")
                StepState.Done -> Ico(Icons.Check, 14.dp, C.Dim, desc = "Finished")
                StepState.Failed -> Ico(Icons.Close, 14.dp, C.Dim, desc = "Did not run")
            }
        }
        T(step.tool, size = 12.sp, color = C.Dim, mono = true, maxLines = 1)
        T(step.detail, Modifier.weight(1f), size = 12.sp, color = C.Sub, mono = true, maxLines = 2)
    }
}

@Composable
private fun Approval(approval: Approval, host: String, onAnswer: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().enter(dy = (-8).dp, ms = 380).card(C.AmberBg, C.AmberBorder, 14.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("NEEDS YOU", size = 11.sp, color = C.Amber, mono = true, spacing = .06.em)
            T("Run this on $host?", weight = W5)
            if (approval.description.isNotBlank()) T(approval.description, size = 13.sp, color = C.Sub)
        }
        if (approval.command.isNotBlank()) {
            T(
                approval.command,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Bg).padding(horizontal = 12.dp, vertical = 10.dp),
                size = 12.sp, mono = true,
            )
        }
        SlideToApprove { onAnswer("once") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ghost("Deny", Modifier.weight(1f).height(44.dp)) { onAnswer("deny") }
            if ("session" in approval.choices) Ghost("This session", Modifier.weight(1f).height(44.dp)) { onAnswer("session") }
            if ("always" in approval.choices) Ghost("Always", Modifier.weight(1f).height(44.dp)) { onAnswer("always") }
        }
    }
}

@Composable
private fun Ghost(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.press(onClick = onClick).border(1.dp, C.BorderStrong, shape).padding(horizontal = 12.dp), Alignment.Center) {
        T(label, size = 14.sp, weight = W5, align = TextAlign.Center, maxLines = 1)
    }
}

/** Risky commands need a deliberate drag. Let go early and the handle springs back. */
@Composable
private fun SlideToApprove(onApprove: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val clock = LocalClock.current
    val reduce = LocalReduceMotion.current
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).background(C.Bg).border(1.dp, C.AmberTrack, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "Slide to approve this command"
                onClick("Approve") { onApprove(); true }
            },
    ) {
        val travel = maxWidth - 60.dp
        val travelPx = with(LocalDensity.current) { travel.toPx() }
        var fired by remember { mutableStateOf(false) }
        Box(
            Modifier.fillMaxHeight().width(58.dp + travel * progress.value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(11.dp)).background(C.AmberTrack),
        )
        T(
            "Slide to approve",
            Modifier.align(Alignment.Center).padding(start = 40.dp)
                .graphicsLayer { alpha = (1f - progress.value * 2.2f).coerceIn(0f, 1f) },
            size = 14.sp, weight = W5, color = C.Amber,
        )
        Box(
            Modifier.offset { IntOffset((4.dp.toPx() + travelPx * progress.value).roundToInt(), 5.dp.roundToPx()) }
                .size(52.dp, 42.dp).clip(RoundedCornerShape(9.dp)).background(C.Amber)
                .pointerInput(travelPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { progress.animateTo(0f, tween(320, easing = SpringEase)) } },
                        onDragCancel = { scope.launch { progress.animateTo(0f, tween(320, easing = SpringEase)) } },
                    ) { change, amount ->
                        change.consume()
                        val next = (progress.value + amount / travelPx).coerceIn(0f, 1f)
                        if (next >= .96f && !fired) {
                            fired = true
                            onApprove()
                        } else {
                            scope.launch { progress.snapTo(next) }
                        }
                    }
                },
            Alignment.Center,
        ) {
            Ico(Icons.ArrowRight, 20.dp, C.Bg, Modifier.graphicsLayer {
                if (!reduce) {
                    val p = frac(clock.floatValue / 2.4f)
                    val k = when {
                        p < .7f -> 0f
                        p < .82f -> (p - .7f) / .12f
                        else -> 1f - (p - .82f) / .18f
                    }
                    translationX = k * 7.dp.toPx()
                }
            })
        }
    }
}
