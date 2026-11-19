package com.zyphite.coma.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.data.Found
import com.zyphite.coma.data.Hermes
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Field
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.IconBtn
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.Lane
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.W6
import com.zyphite.coma.ui.Wing
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.rowPress
import com.zyphite.coma.ui.topLine
import com.zyphite.coma.ui.ts

private const val COMMAND = "hermes coma"

/**
 * First run, and "add a host". The host runs the coma plugin (hermes coma, or the Coma page in Hermes Desktop), which opens a Cloudflare tunnel to its Hermes
 * and shows a QR of `coma://connect?url=…&key=…&name=…`; scanning it pairs at once. The address and
 * key can also be typed, for a host behind your own proxy. Hosts with pairing started on the same network
 * are found by themselves and pair with the six-digit code they show. [onBack] is null on first run.
 */
@Composable
fun ConnectScreen(vm: ComaViewModel, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val context = LocalContext.current
    var byHand by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Found?>(null) }
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.search() }
    val pair = {
        picked?.let { host ->
            error = null
            vm.pairWith(host, code) { failure ->
                error = failure
                if (failure == null) onDone()
            }
        }
        Unit
    }

    val connect = { url: String, secret: String, name: String? ->
        if (!vm.connecting) {
            error = null
            vm.connect(url, secret, name) { failure ->
                error = failure
                if (failure == null) onDone()
            }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents?.trim().orEmpty()
        val link = Uri.parse(text)
        when {
            text.isEmpty() -> Unit
            link.scheme == "coma" && link.getQueryParameter("url") != null -> connect(
                link.getQueryParameter("url").orEmpty(), link.getQueryParameter("key").orEmpty(), link.getQueryParameter("name"),
            )
            Hermes.address(text) != null -> { byHand = true; address = text; error = null }
            else -> error = "That QR code isn't a Coma pairing code."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 24.dp, top = if (onBack == null) 52.dp else 8.dp, end = 24.dp, bottom = 16.dp),
    ) {
        if (onBack != null) {
            IconBtn(Icons.ChevronLeft, "Back", onBack, Modifier.padding(bottom = 8.dp).offset(x = (-12).dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(Icons.Phone, 26.dp, C.Sub, desc = "This phone")
            Lane(Modifier.weight(1f))
            Wing(if (vm.connecting) Signal.Think else Signal.Idle, 30.dp, label = "Hermes")
            Lane(Modifier.weight(1f).alpha(.35f), live = vm.connecting)
            Ico(Icons.Server, 26.dp, C.Sub, desc = "Your host")
        }

        T(
            "Pick up your Hermes where it lives.", Modifier.padding(top = 44.dp),
            size = 30.sp, weight = W6, spacing = (-0.03).em, lineHeight = 34.sp,
        )
        T(
            "Open Coma in Hermes Desktop, or run this on the machine that hosts Hermes, then scan the code it shows.",
            Modifier.padding(top = 12.dp), color = C.Sub, lineHeight = 21.sp,
        )

        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().height(52.dp).card().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("$", color = C.Dim, mono = true)
            T(COMMAND, Modifier.weight(1f), size = 14.sp, mono = true)
            IconBtn(Icons.Copy, "Copy command", {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("command", COMMAND))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }, dp = 18.dp, tint = C.Sub)
        }

        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp)
                .press(.96f) {
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
                            .setOrientationLocked(false).setPrompt("Scan the code that Hermes shows"),
                    )
                }
                .clip(RoundedCornerShape(12.dp)).background(C.Text),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Icons.Qr, 18.dp, C.Bg)
            T(if (vm.connecting) "Connecting" else "Scan pairing code", color = C.Bg, weight = W6)
        }
        if (error != null) T(error.orEmpty(), Modifier.padding(top = 8.dp), size = 13.sp, color = C.Amber)

        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            T("On this network", Modifier.weight(1f), size = 13.sp, weight = W5, color = C.Sub)
            T(
                if (vm.searching) "searching" else "search again",
                Modifier.press { vm.search() }.padding(vertical = 8.dp), size = 12.sp, color = C.Dim, mono = true,
            )
        }
        if (vm.found.isEmpty()) {
            T(
                if (vm.searching) "Looking for a Hermes that has pairing started." else "No Hermes with pairing started answered on this Wi-Fi.",
                Modifier.padding(top = 4.dp), size = 14.sp, color = C.Dim,
            )
        }
        vm.found.forEach { host ->
            val open = picked == host
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().card()) {
                Row(
                    Modifier.fillMaxWidth().rowPress { picked = if (open) null else host; code = ""; error = null }
                        .heightIn(min = 60.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Ico(Icons.Server, 22.dp, C.Sub)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(host.name, weight = W5, maxLines = 1)
                        T(host.ip, size = 12.sp, color = C.Sub, mono = true)
                    }
                    Ico(if (open) Icons.ChevronDown else Icons.ChevronRight, 18.dp, C.Dim)
                }
                if (open) {
                    Column(Modifier.fillMaxWidth().topLine(C.Border).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T("Type the pairing code ${host.name} is showing.", size = 13.sp, color = C.Sub)
                        Field(
                            code, { code = it.filter(Char::isDigit).take(6); error = null }, "000000",
                            Modifier.fillMaxWidth(),
                            style = ts(28.sp, mono = true, align = TextAlign.Center, spacing = .42.em), placeholderColor = C.Mute,
                            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                            desc = "Pairing code", onDone = pair,
                        )
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).press(.96f, onClick = pair)
                                .clip(RoundedCornerShape(12.dp)).background(C.Text),
                            Alignment.Center,
                        ) { T(if (vm.connecting) "Connecting" else "Pair", color = C.Bg, weight = W6) }
                    }
                }
            }
        }

        if (!byHand) {
            Outline(Icons.Link, "Enter an address by hand", Modifier.padding(top = 12.dp).fillMaxWidth()) { byHand = true }
        } else {
            T("Host address", Modifier.padding(top = 24.dp), size = 13.sp, weight = W5, color = C.Sub)
            Input {
                Field(
                    address, { address = it; error = null }, "192.168.1.20:8642",
                    style = ts(16.sp, mono = true), placeholderColor = C.Mute,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    desc = "Host address",
                )
            }
            T("API key", Modifier.padding(top = 14.dp), size = 13.sp, weight = W5, color = C.Sub)
            Input {
                Field(
                    key, { key = it; error = null }, "API_SERVER_KEY",
                    style = ts(16.sp, mono = true), placeholderColor = C.Mute,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    desc = "API key", onDone = { connect(address, key, null) },
                )
            }
            Outline(Icons.ArrowRight, "Connect", Modifier.padding(top = 12.dp).fillMaxWidth()) { connect(address, key, null) }
        }

        Spacer(Modifier.weight(1f).heightIn(min = 28.dp))
        T(
            "Threads, memory and keys stay on your host. Pairing opens an encrypted Cloudflare tunnel to it; " +
                "nothing is stored in between.",
            size = 13.sp, color = C.Dim,
        )
    }
}

@Composable
private fun Input(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp).clip(shape).background(C.Surface)
            .border(1.dp, C.BorderStrong, shape).padding(horizontal = 14.dp),
        Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun Outline(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).press(.96f, onClick = onClick).card(bg = C.Bg),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, 18.dp)
        T(label, size = 14.sp, weight = W5)
    }
}
