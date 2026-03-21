package com.hu3h.biktv.data.session

import kotlinx.coroutines.flow.Flow

interface NcmSessionStore {
    val sessionFlow: Flow<NcmSession?>
    suspend fun saveSession(session: NcmSession)
    suspend fun updateCookie(cookie: String, csrf: String?)
    suspend fun clear()
}
