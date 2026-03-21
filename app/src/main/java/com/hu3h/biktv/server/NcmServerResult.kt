package com.hu3h.biktv.server

data class NcmServerResult(
    val status: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8"
)
