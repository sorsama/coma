package com.zyphite.coma.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** The voice button: the phone's own speech recognition turns what you say into a message for [onText]. */
@Composable
fun rememberDictation(onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val deliver = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { deliver.value(it) }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Hermes")
        try {
            launcher.launch(intent)
        } catch (missing: ActivityNotFoundException) {
            Toast.makeText(context, "This phone has no speech recognition installed.", Toast.LENGTH_SHORT).show()
        }
    }
}
