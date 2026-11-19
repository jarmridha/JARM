package com.jarm.app.data.repository

import com.jarm.app.model.ConversationTodo
import com.jarm.app.model.ConversationTodoItem
import com.jarm.app.model.RunEffectIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

fun ConversationRepository.observeConversationTodo(id: String): Flow<List<ConversationTodoItem>> =
    chatDao.observeConversationTodo(id).distinctUntilChanged().map { ConversationTodo.decode(it) }

suspend fun ConversationRepository.readConversationTodo(id: String): List<ConversationTodoItem> =
    ConversationTodo.decode(chatDao.getConversationTodo(id))

suspend fun ConversationRepository.updateConversationTodo(
    identity: RunEffectIdentity, items: List<ConversationTodoItem>,
): Boolean = chatDao.updateConversationTodo(
    identity.conversationId, ConversationTodo.encode(items), identity.runId, identity.pass,
    System.currentTimeMillis(),
) == 1
