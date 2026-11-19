package com.zyphite.coma.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.billing.BillingManager
import com.zyphite.coma.ui.BackHeader
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.IconBtn
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.Radio
import com.zyphite.coma.ui.Sheet
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.W6
import com.zyphite.coma.ui.Wing
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.growX
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.rowPress
import com.zyphite.coma.ui.topLine

private fun Context.activity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The hosts this phone can reach, and the plan: one host free, more with Plus at 99 baht a month. */
@Composable
fun HostsScreen(vm: ComaViewModel, onBack: () -> Unit, onConnect: () -> Unit) {
    val context = LocalContext.current
    val billing = vm.billing
    val free = ComaViewModel.FREE_HOSTS
    var plan by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { billing.connect() }
    // The sheet has done its job the moment the purchase lands.
    LaunchedEffect(billing.isPlus) { if (billing.isPlus) plan = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            BackHeader("Hosts", "Back to System", onBack, trailing = if (billing.isPlus) "plus" else "free plan")

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Column(
                    Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().card().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("Hosts on this phone", Modifier.weight(1f).alignByBaseline(), weight = W5)
                        T(
                            if (billing.isPlus) "${vm.hosts.size} · unlimited" else "${vm.hosts.size} of $free",
                            Modifier.alignByBaseline(), size = 13.sp, mono = true,
                        )
                    }
                    Row(
                        Modifier.semantics { contentDescription = "${vm.hosts.size} of $free free hosts used" },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(free) { slot ->
                            val used = slot < vm.hosts.size
                            Box(
                                Modifier.weight(1f).height(4.dp).then(if (used) Modifier.growX(slot * 80) else Modifier)
                                    .clip(RoundedCornerShape(2.dp)).background(if (used) C.Text else C.Thumb),
                            )
                        }
                    }
                    T(
                        if (billing.isPlus) "Each host is a machine running Hermes that this phone is paired with. Plus covers as many as you pair."
                        else "Each host is a machine running Hermes that this phone is paired with. One host is free.",
                        size = 13.sp, color = C.Sub,
                    )
                }

                vm.hosts.forEachIndexed { index, host ->
                    Row(
                        Modifier.fillMaxWidth().topLine().rowPress(role = Role.RadioButton) { vm.pickHost(index) }
                            .heightIn(min = 68.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val active = vm.host == host
                        val down = active && vm.online == false
                        Wing(if (down) Signal.Off else Signal.Idle, 16.dp, label = if (down) "Unreachable" else "Host")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            T(host.name, weight = W5, maxLines = 1)
                            T(
                                listOfNotNull(
                                    host.url.substringAfter("://"),
                                    when { !active -> null; vm.online == false -> "unreachable"; vm.online == true -> "online"; else -> "checking" },
                                ).joinToString(" · "),
                                size = 12.sp, color = C.Sub, mono = true, maxLines = 1,
                            )
                        }
                        IconBtn(Icons.Close, "Remove ${host.name}", { vm.removeHost(index) }, dp = 16.dp, tint = C.Dim)
                        Radio(vm.host == host)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().topLine().padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).press(.96f) { if (vm.canAddHost) onConnect() else plan = true }
                            .clip(RoundedCornerShape(26.dp)).background(C.Text),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Ico(Icons.Plus, 18.dp, C.Bg)
                        T("Add a host", weight = W6, color = C.Bg)
                    }
                    if (billing.isPlus) {
                        Box(
                            Modifier.fillMaxWidth().height(44.dp).press(.96f) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BillingManager.MANAGE_URL)))
                            },
                            Alignment.Center,
                        ) { T("Manage Plus in Google Play", size = 13.sp, color = C.Sub, decoration = TextDecoration.Underline) }
                    } else {
                        Box(Modifier.fillMaxWidth().height(44.dp).press(.96f) { plan = true }, Alignment.Center) {
                            T(
                                "Plus: unlimited hosts, ${billing.price} a month",
                                size = 13.sp, color = C.Sub, decoration = TextDecoration.Underline,
                            )
                        }
                    }
                }
            }
        }

        Sheet(plan, { plan = false }) {
            Column(
                Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.padding(start = 4.dp)) {
                    T(
                        if (vm.canAddHost) "Add as many hosts as you like" else "Your free host is in use",
                        Modifier.weight(1f).padding(top = 12.dp), size = 20.sp, weight = W6, spacing = (-0.02).em,
                    )
                    IconBtn(Icons.Close, "Close", { plan = false }, dp = 20.dp, tint = C.Sub)
                }
                T(
                    "Plus lifts the limit. Nothing changes for the hosts you already have.",
                    Modifier.padding(horizontal = 4.dp), size = 14.sp, color = C.Sub,
                )

                val plusShape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().border(1.dp, C.Mute, plusShape).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("Plus", Modifier.weight(1f).alignByBaseline(), weight = W6)
                        T("${billing.price} / month", Modifier.alignByBaseline(), size = 13.sp, mono = true)
                    }
                    T(
                        "Unlimited hosts on this phone. Renews monthly until you cancel in Google Play.",
                        size = 14.sp, color = C.Sub,
                    )
                    Box(
                        Modifier.fillMaxWidth().height(48.dp)
                            .press(.96f) { context.activity()?.let(billing::purchase) }
                            .clip(RoundedCornerShape(24.dp)).background(C.Text),
                        Alignment.Center,
                    ) { T("Upgrade to Plus", weight = W6, color = C.Bg, align = TextAlign.Center) }
                    billing.message?.let { T(it, size = 13.sp, color = C.Amber) }
                }
            }
        }
    }
}
