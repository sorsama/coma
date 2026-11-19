package com.zyphite.coma.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.RunState
import com.zyphite.coma.data.ModelChoice
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.GeistMono
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.IconBtn
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.Radio
import com.zyphite.coma.ui.Segmented
import com.zyphite.coma.ui.Sheet
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.enter
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.rowPress
import com.zyphite.coma.ui.topLine
import com.zyphite.coma.ui.ts

/**
 * The composer: a message, the commands this app carries out itself, and the model for new turns.
 * Hermes does not read slash commands sent through its API, so each one here is an action of the app.
 * [section] null hides the sheet; [inChat] says a thread is open behind it.
 */
@Composable
fun ComposerSheet(
    vm: ComaViewModel,
    section: Int?,
    inChat: Boolean,
    onClose: () -> Unit,
    onSend: (String) -> Unit,
    onNew: () -> Unit,
    onSkills: () -> Unit,
) {
    Sheet(section != null, onClose, Modifier.fillMaxHeight(.9f)) {
        var tab by remember(section) { mutableIntStateOf(section ?: 0) }
        var input by remember { mutableStateOf(TextFieldValue("")) }
        val running = vm.current?.takeIf { inChat && it.state != RunState.Idle }

        val commands: List<Triple<String, String, () -> Unit>> = listOfNotNull(
            Triple("/new", "Start a fresh thread", onNew),
            running?.let { Triple("/stop", "Halt the current run") { vm.stop(it); onClose() } },
            Triple("/model", "Choose the model for new turns") { tab = 1 },
            Triple("/skills", "Browse what Hermes knows how to do", onSkills),
        )
        val send = {
            val text = input.text.trim()
            val command = commands.firstOrNull { it.first == text.lowercase() }
            input = TextFieldValue("")
            when {
                command != null -> command.third()
                text.isNotEmpty() -> onSend(text)
            }
        }

        Row(Modifier.padding(start = 20.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                buildAnnotatedString {
                    append("To ")
                    withStyle(SpanStyle(color = C.Text, fontFamily = GeistMono)) { append("hermes") }
                    append(" on ${vm.host?.name.orEmpty()}")
                },
                Modifier.weight(1f), style = ts(13.sp, C.Sub),
            )
            IconBtn(Icons.Close, "Close composer", onClose, dp = 20.dp, tint = C.Sub)
        }

        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.weight(1f).height(52.dp).clip(shape).background(C.Bg).border(1.dp, C.BorderStrong, shape)
                    .padding(horizontal = 14.dp),
                Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message or command" },
                    textStyle = ts(15.sp, mono = true),
                    singleLine = true,
                    cursorBrush = SolidColor(C.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onAny = { send() }),
                    decorationBox = { inner ->
                        if (input.text.isEmpty()) T("Ask, or type / for commands", color = C.Dim, mono = true, maxLines = 1)
                        inner()
                    },
                )
            }
            Box(Modifier.size(52.dp).press(.96f, onClick = send).clip(shape).background(C.Text), Alignment.Center) {
                Ico(Icons.ArrowUp, 20.dp, C.Bg, desc = "Send")
            }
        }

        Segmented(
            listOf("Commands", "Model"), tab, { tab = it },
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), bg = C.Bg,
        )

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            key(tab) {
                Column(Modifier.enter(dy = 0.dp, dx = 10.dp, ms = 280)) {
                    if (tab == 0) {
                        val typed = input.text.trim().lowercase()
                        val shown = if (typed.startsWith("/")) commands.filter { it.first.startsWith(typed) } else commands
                        shown.forEachIndexed { index, (command, about, run) ->
                            Row(
                                Modifier.fillMaxWidth().then(if (index == 0) Modifier else Modifier.topLine(C.Line))
                                    .rowPress(C.Chip) { input = TextFieldValue(""); run() }
                                    .heightIn(min = 48.dp).padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                T(command, Modifier.width(104.dp), size = 13.sp, mono = true)
                                T(about, Modifier.weight(1f), size = 14.sp, color = C.Sub, maxLines = 1)
                            }
                        }
                    } else {
                        Models(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun Models(vm: ComaViewModel) {
    LaunchedEffect(vm.host) { if (vm.modelOptions == null) vm.refreshModels() }
    val options = vm.modelOptions
    val failure = vm.modelsError

    ModelRow("Host default", options?.current?.let { "${it.model} · ${it.providerName}" } ?: "whatever the host is set to", vm.model == null) {
        vm.pickModel(null)
    }
    options?.choices?.forEach { choice: ModelChoice ->
        ModelRow(choice.model, choice.providerName, vm.model == choice) { vm.pickModel(choice) }
    }
    T(
        when {
            options != null -> "Only providers the host is signed in to are listed. The choice applies to turns sent from this phone."
            failure != null -> failure
            else -> "Asking the host which models it can use."
        },
        Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp), size = 13.sp, color = C.Sub,
    )
}

@Composable
private fun ModelRow(name: String, via: String, on: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topLine(C.Line).rowPress(C.Chip, Role.RadioButton, onPick)
            .heightIn(min = 60.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(name, weight = W5, maxLines = 1)
            T(via, size = 12.sp, color = C.Sub, mono = true, maxLines = 1)
        }
        Radio(on)
    }
}
