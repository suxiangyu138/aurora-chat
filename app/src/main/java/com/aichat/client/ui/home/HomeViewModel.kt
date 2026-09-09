package com.aichat.client.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.client.ChatApplication
import com.aichat.client.data.local.SessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ChatApplication).sessionRepository

    /** 会话列表(按最近更新时间倒序) */
    val sessions: StateFlow<List<SessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSession(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createSession()
            onCreated(id)
        }
    }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch { repository.renameSession(id, title) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }
}
