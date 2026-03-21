package com.hu3h.biktv.data.session

import kotlinx.coroutines.flow.Flow

interface BiliSessionStore {
    val sessionFlow: Flow<BiliSession?>

    suspend fun saveSession(session: BiliSession)
    suspend fun updateCookie(cookie: String, csrf: String? = null)
    suspend fun clear()
}
