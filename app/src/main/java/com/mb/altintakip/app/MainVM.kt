package com.mb.altintakip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx: Application = app

    val quotes = MutableStateFlow<List<Quote>>(emptyList())
    val status = MutableStateFlow("Yükleniyor…")
    val holdings = MutableStateFlow<List<Holding>>(emptyList())