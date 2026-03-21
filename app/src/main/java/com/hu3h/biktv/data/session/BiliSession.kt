package com.hu3h.biktv.data.session

data class BiliSession(
    val cookie: String,
    val csrf: String? = null,
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val level: Int? = null,
    val expiresAt: Long? = null,
    val refreshToken: String? = null,
    val extraParamsJson: String? = null
)
