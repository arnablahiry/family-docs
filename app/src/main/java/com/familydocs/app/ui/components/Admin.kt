package com.familydocs.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Whether you're an admin of group [groupId]: admins can change other members' documents and profiles, and manage the group. */
@Composable
fun rememberIsAdmin(groupId: String?): Boolean {
    val app = LocalContainer.current
    val groups by app.repo.groups.collectAsStateWithLifecycle(emptyList())
    val self by app.repo.self().collectAsStateWithLifecycle(null)
    return groups.firstOrNull { it.id == groupId }?.isAdmin(self?.id) == true
}

/** The group a received row ("s:<group>:<member>:<id>") came from. */
fun groupOfShared(id: String): String? = if (id.startsWith("s:")) id.split(':').getOrNull(1) else null
