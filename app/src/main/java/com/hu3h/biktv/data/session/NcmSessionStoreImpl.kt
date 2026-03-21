package com.hu3h.biktv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "ncm_session_store"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME
)

class NcmSessionStoreImpl(
    private val context: Context
) : NcmSessionStore {

    override val sessionFlow: Flow<NcmSession?> =
        context.sessionDataStore.data.map { prefs ->
            val cookie = prefs[Keys.COOKIE] ?: return@map null
            NcmSession(
                cookie = cookie,
                csrf = prefs[Keys.CSRF],
                userId = prefs[Keys.USER_ID],
                nickname = prefs[Keys.NICKNAME],
                avatarUrl = prefs[Keys.AVATAR_URL],
                expiresAt = prefs[Keys.EXPIRES_AT]
            )
        }

    override suspend fun saveSession(session: NcmSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.COOKIE] = session.cookie
            setOrClear(prefs, Keys.CSRF, session.csrf)
            setOrClear(prefs, Keys.USER_ID, session.userId)
            setOrClear(prefs, Keys.NICKNAME, session.nickname)
            setOrClear(prefs, Keys.AVATAR_URL, session.avatarUrl)
            setOrClear(prefs, Keys.EXPIRES_AT, session.expiresAt)
        }
    }

    override suspend fun updateCookie(cookie: String, csrf: String?) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.COOKIE] = cookie
            setOrClear(prefs, Keys.CSRF, csrf)
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun setOrClear(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = value
        }
    }

    private fun setOrClear(prefs: MutablePreferences, key: Preferences.Key<Long>, value: Long?) {
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = value
        }
    }

    private object Keys {
        val COOKIE = stringPreferencesKey("ncm_cookie")
        val CSRF = stringPreferencesKey("ncm_csrf")
        val USER_ID = stringPreferencesKey("ncm_user_id")
        val NICKNAME = stringPreferencesKey("ncm_nickname")
        val AVATAR_URL = stringPreferencesKey("ncm_avatar_url")
        val EXPIRES_AT = longPreferencesKey("ncm_expires_at")
    }
}
