package com.zyphite.coma.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyphite.coma.ComaViewModel
import com.zyphite.coma.data.Job
import com.zyphite.coma.ui.C
import com.zyphite.coma.ui.Field
import com.zyphite.coma.ui.Ico
import com.zyphite.coma.ui.Icons
import com.zyphite.coma.ui.LocalClock
import com.zyphite.coma.ui.LocalReduceMotion
import com.zyphite.coma.ui.ScreenHeader
import com.zyphite.coma.ui.Signal
import com.zyphite.coma.ui.T
import com.zyphite.coma.ui.Toggle
import com.zyphite.coma.ui.W5
import com.zyphite.coma.ui.card
import com.zyphite.coma.ui.frac
import com.zyphite.coma.ui.press
import com.zyphite.coma.ui.rowPress
import com.zyphite.coma.ui.topLine
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** When the job next runs, in this phone's time zone, or null when the host did not say. */
private fun Job.next() = runCatching { OffsetDateTime.parse(nextRun).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

/**
 * The host's scheduled jobs. A new one is described in plain words and handed to Hermes in a thread,
 * which sets it up with its own cron tool; [onAsk] opens that thread.
 */
@Composable
fun AutomateScreen(vm: ComaViewModel, onAsk: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val now = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val add = {
        if (draft.isNotBlank()) {
            onAsk("Set this up as a scheduled job with your cron tool: ${draft.trim()}")
            draft = ""
        }
    }

    LaunchedEffect(vm.host) { vm.refreshJobs() }

    val upcoming = vm.jobs.filter { it.enabled }.mapNotNull { it.next() }.sorted()
    val today = upcoming.filter { it.toLocalDate() == LocalDate.now() }.map { it.hour * 60 + it.minute }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Automate", if (upcoming.isEmpty()) Signal.Idle else Signal.Tick,
            if (upcoming.isEmpty()) "Nothing scheduled" else "Runs are scheduled",
        ) {
            upcoming.firstOrNull()?.let {
                T("next %02d:%02d".format(it.hour, it.minute), size = 12.sp, color = C.Sub, mono = true)
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            DayLine(now, today)

            vm.jobs.forEach { job ->
                val fade by animateFloatAsState(if (job.enabled) 1f else .55f, tween(200), label = "fade")
                Row(
                    Modifier.fillMaxWidth().topLine().rowPress(role = Role.Switch) { vm.setJobEnabled(job, !job.enabled) }
                        .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f).graphicsLayer { alpha = fade }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(job.name, weight = W5, maxLines = 1)
                        T(
                            listOfNotNull(
                                job.schedule,
                                if (job.enabled) null else "paused",
                                job.lastStatus.takeIf { it.isNotBlank() }?.let { "last $it" },
                            ).joinToString(" · "),
                            size = 12.sp, color = C.Sub, mono = true, maxLines = 1,
                        )
                    }
                    Toggle(job.enabled)
                }
            }

            val failure = vm.jobsError
            if (vm.jobs.isEmpty() || failure != null) {
                T(
                    failure ?: "No scheduled jobs on this host. Describe one below and Hermes will set it up.",
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
                    color = C.Sub, align = TextAlign.Center,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().topLine().padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(1f).height(48.dp).card(C.Surface2).padding(horizontal = 14.dp),
                Alignment.CenterStart,
            ) {
                Field(
                    draft, { draft = it }, "Every Friday at 6pm, send me…",
                    keyboard = KeyboardOptions(imeAction = ImeAction.Done),
                    desc = "Describe a new automation", onDone = add,
                )
            }
            Box(
                Modifier.size(48.dp).press(onClick = add).clip(RoundedCornerShape(12.dp)).background(C.Text),
                Alignment.Center,
            ) { Ico(Icons.Plus, 20.dp, C.Bg, desc = "Create automation") }
        }
    }
}

/** Today as a line: the moment now and the runs still to come. Both are minutes after midnight. */
@Composable
private fun DayLine(now: Int, runs: List<Int>) {
    val clock = LocalClock.current
    val reduce = LocalReduceMotion.current
    Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp)) {
        Canvas(Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = "Runs left today: ${runs.size}" }) {
            val y = 16.5.dp.toPx()
            val at = size.width * now / 1440f
            drawLine(C.Border2, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            drawLine(C.Dim, Offset(0f, y), Offset(at, y), 1.dp.toPx())
            runs.forEach { minute ->
                val x = size.width * minute / 1440f
                val tick = Size(3.dp.toPx(), 17.dp.toPx())
                val corner = CornerRadius(1.5.dp.toPx())
                drawRoundRect(C.Bg, Offset(x, 8.dp.toPx()), tick, corner)
                drawRoundRect(C.Sub, Offset(x, 8.dp.toPx()), tick, corner, style = Stroke(1.dp.toPx()))
            }
            drawCircle(C.Text, 4.5.dp.toPx(), Offset(at, y))
            if (!reduce) {
                val p = frac(clock.floatValue / 2f)
                drawCircle(C.Text, 9.5.dp.toPx() * (.6f + 1.2f * p), Offset(at, y), alpha = .8f * (1f - p), style = Stroke(1.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach { T(it, size = 11.sp, color = C.Dim, mono = true) }
        }
    }
}
