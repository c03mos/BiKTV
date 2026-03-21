package com.hu3h.biktv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "bili_session_store"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME
)

class BiliSessionStoreImpl(
    private val context: Context
) : BiliSessionStore {

    override val sessionFlow: Flow<BiliSession?> =
        context.sessionDataStore.data.map { prefs ->
            val cookie = prefs[Keys.COOKIE] ?: return@map null
            BiliSession(
                cookie = cookie,
                csrf = prefs[Keys.CSRF],
                userId = prefs[Keys.USER_ID],
                nickname = prefs[Keys.NICKNAME],
                avatarUrl = prefs[Keys.AVATAR_URL],
                level = prefs[Keys.LEVEL],
                expiresAt = prefs[Keys.EXPIRES_AT],
                refreshToken = prefs[Keys.REFRESH_TOKEN],
                extraParamsJson = prefs[Keys.EXTRA_JSON]
            )
        }

    override suspend fun saveSession(session: BiliSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.COOKIE] = session.cookie
            setOrClear(prefs, Keys.CSRF, session.csrf)
            setOrClear(prefs, Keys.USER_ID, session.userId)
            setOrClear(prefs, Keys.NICKNAME, session.nickname)
            setOrClear(prefs, Keys.AVATAR_URL, session.avatarUrl)
            setOrClear(prefs, Keys.LEVEL, session.level)
            setOrClear(prefs, Keys.REFRESH_TOKEN, session.refreshToken)
            setOrClear(prefs, Keys.EXTRA_JSON, session.extraParamsJson)
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

    private fun setOrClear(prefs: MutablePreferences, key: Preferences.Key<Int>, value: Int?) {
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = value
        }
    }

    private object Keys {
        val COOKIE = stringPreferencesKey("bili_cookie")
        val CSRF = stringPreferencesKey("bili_csrf")
        val USER_ID = stringPreferencesKey("bili_user_id")
        val NICKNAME = stringPreferencesKey("bili_nickname")
        val AVATAR_URL = stringPreferencesKey("bili_avatar_url")
        val LEVEL = intPreferencesKey("bili_level")
        val REFRESH_TOKEN = stringPreferencesKey("bili_refresh_token")
        val EXTRA_JSON = stringPreferencesKey("bili_extra_json")
        val EXPIRES_AT = longPreferencesKey("bili_expires_at")
    }
}
