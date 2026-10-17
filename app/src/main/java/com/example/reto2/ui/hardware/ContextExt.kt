package com.example.reto2.ui.hardware

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

// Busca la Activity detrás del Context de Compose.
tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}