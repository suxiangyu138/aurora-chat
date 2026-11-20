package com.aichat.client.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.local.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 首页:会话列表 + 新建会话 + 入口设置页 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTutorial: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selecting) "已选 ${selectedIds.size} 项" else "Aurora Chat")
                },
                actions = {
                    if (selecting) {
                        val allSelected = sessions.isNotEmpty() &&
                            selectedIds.size == sessions.size
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet()
                            else sessions.map { it.id }.toSet()
                        }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选"
                            )
                        }
                        IconButton(onClick = { batchDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除所选")
                        }
                        IconButton(onClick = {
                            selecting = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    } else {
                    IconButton(onClick = {
                        viewModel.createAnonymousSession { onOpenSession(it) }
                    }) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "匿名会话(退出即删)")
                    }
                    IconButton(onClick = onOpenTutorial) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = "使用教程"
                        )
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Default.Info, contentDescription = "关于与开发者信息")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "模型配置")
                    }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createSession { onOpenSession(it) } }) {
                Icon(Icons.Default.Add, contentDescription = "新建会话")
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有会话\n点击右下角 + 开始对话",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    ListItem(
                        headlineContent = {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(formatTime(session.updatedAt)) },
                        leadingContent = if (selecting) {
                            {
                                Checkbox(
                                    checked = selectedIds.contains(session.id),
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + session.id
                                        } else selectedIds - session.id
                                    }
                                )
                            }
                        } else null,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selecting) {
                                    selectedIds = if (selectedIds.contains(session.id)) {
                                        selectedIds - session.id
                                    } else selectedIds + session.id
                                } else {
                                    onOpenSession(session.id)
                                }
                            },
                            onLongClick = {
                                selecting = true
                                selectedIds = selectedIds + session.id
                            }
                        ),
                        trailingContent = if (selecting) {
                            null
                        } else {
                            {
                                Row {
                                    IconButton(onClick = {
                                        newTitle = session.title
                                        renameTarget = session
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "重命名")
                                    }
                                    IconButton(onClick = { deleteTarget = session }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // 重命名对话框
    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSession(session.id, newTitle.trim().ifBlank { session.title })
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 批量删除确认
    if (batchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { batchDeleteConfirm = false },
            title = { Text("删除所选会话") },
            text = { Text("将删除 ${selectedIds.size} 个会话及其全部消息,确定继续?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSessions(selectedIds)
                    batchDeleteConfirm = false
                    selecting = false
                    selectedIds = emptySet()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { batchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 删除确认对话框
    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${session.title}」及其全部消息?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
