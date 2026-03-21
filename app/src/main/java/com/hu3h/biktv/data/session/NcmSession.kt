package com.hu3h.biktv.data.session

data class NcmSession(
    val cookie: String,
    val csrf: String? = null,
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val expiresAt: Long? = null
)
