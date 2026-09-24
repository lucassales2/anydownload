package com.anydownlod.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.anydownlod.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The real Android graph: shared HTTP engine for direct files and
            // the Chaquopy adapter for other URLs. remember keeps one graph
            // (and its engine scope) for the activity lifetime.
            val graph = remember { AndroidAppGraph(applicationContext) }
            App(graph = graph, offerClipboardCheck = true)
        }
    }
}