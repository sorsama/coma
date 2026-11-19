package com.zyphite.coma.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Field
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.ScreenHeader
import com.zyphite.coma.ui.Segmented
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.bottomLine
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.enter

/** What Hermes can do on this host: the skills it has installed and the toolsets switched on for API turns. */
@Composable
fun MindScreen(vm: ComaViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(vm.host) {
        vm.refreshSkills()
        vm.refreshToolsets()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mind", Signal.Learn, "What Hermes knows how to do")
        Segmented(listOf("Skills", "Tools"), tab, { tab = it }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp).fillMaxWidth().height(48.dp).card(C.Surface2)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Ico(Icons.Search, 18.dp, C.Dim)
            val hint = if (tab == 0) "Search skills" else "Search tools"
            Field(query, { query = it }, hint, Modifier.weight(1f), desc = hint)
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            key(tab) {
                Column(Modifier.enter(dy = 0.dp, dx = 10.dp, ms = 280).padding(bottom = 20.dp)) {
                    val needle = query.trim()
                    if (tab == 0) {
                        val shown = vm.skills.filter {
                            needle.isEmpty() || it.name.contains(needle, true) || it.description.contains(needle, true)
                        }
                        shown.forEach { Entry(it.name, it.description, it.category, dim = false) }
                        if (shown.isEmpty()) {
                            Empty(vm.skillsError?.let { "This host couldn't list its skills: $it" } ?: "No skills match.")
                        }
                    } else {
                        val shown = vm.toolsets.filter {
                            needle.isEmpty() || it.name.contains(needle, true) || it.label.contains(needle, true)
                        }
                        shown.forEach {
                            Entry(it.name, it.label, if (it.enabled) "${it.tools} on" else "off", dim = !it.enabled)
                        }
                        if (shown.isEmpty()) Empty(vm.toolsetsError ?: "No tools match.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(name: String, about: String, tail: String, dim: Boolean) {
    Row(
        Modifier.fillMaxWidth().bottomLine().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp)
            .graphicsLayer { alpha = if (dim) .55f else 1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(name, size = 14.sp, weight = W5, mono = true, maxLines = 1)
            if (about.isNotBlank()) T(about, size = 13.sp, color = C.Sub, maxLines = 2)
        }
        T(tail, size = 12.sp, color = C.Sub, mono = true, maxLines = 1)
    }
}

@Composable
private fun Empty(text: String) {
    T(text, Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp), color = C.Sub, align = TextAlign.Center)
}
