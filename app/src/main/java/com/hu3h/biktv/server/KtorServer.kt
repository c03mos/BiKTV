package com.hu3h.biktv.server

import android.content.Context
import com.hu3h.biktv.player.KtvPlayerManager
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.request.path
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object KtorServer {
    private var engine: ApplicationEngine? = null
    private val sockets = CopyOnWriteArraySet<WebSocketSession>()
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, port: Int) {
        if (engine != null) return
        appContext = context.applicationContext
        KtvPlayerManager.init(context)
        engine = embeddedServer(CIO, port = port) {
            install(CORS) {
                anyHost()
                allowNonSimpleContentTypes = true
            }
            install(WebSockets)
            module()
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        sockets.clear()
    }

    fun broadcast(message: String) {
        if (message.isBlank()) return
        scope.launch {
            val dead = mutableListOf<WebSocketSession>()
            sockets.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (_: Throwable) {
                    dead.add(session)
                }
            }
            dead.forEach { sockets.remove(it) }
        }
    }

    private fun Application.module() {
        routing {
            get("/remote") {
                serveAsset(call, "remote/index.html", ContentType.Text.Html)
            }
            get("/remote/") {
                serveAsset(call, "remote/index.html", ContentType.Text.Html)
            }
            get("/remote/index.html") {
                serveAsset(call, "remote/index.html", ContentType.Text.Html)
            }
            webSocket("/ws") {
                sockets.add(this)
                try {
                    send(Frame.Text(KtvWsPayloads.buildSnapshot()))
                } catch (_: Throwable) {
                }
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            frame.readText()
                        }
                    }
                } finally {
                    sockets.remove(this)
                }
            }
            get("{...}") {
                call.handleRequest()
            }
            post("{...}") {
                call.handleRequest()
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.handleRequest() {
        val uri = request.path()
        val params = request.queryParameters.entries().associate { entry ->
            entry.key to (entry.value.firstOrNull().orEmpty())
        }
        val body = runCatching { receiveText() }.getOrNull()
        val context = appContext ?: return
        val result = NcmServerHelper.handle(context, uri, params, body)
        respondText(
            text = result.body,
            status = HttpStatusCode.fromValue(result.status),
            contentType = ContentType.parse(result.contentType)
        )
    }

    private suspend fun serveAsset(
        call: io.ktor.server.application.ApplicationCall,
        name: String,
        contentType: ContentType
    ) {
        val ctx = appContext ?: return
        val data = runCatching { ctx.assets.open(name).readBytes() }.getOrNull()
            ?: return call.respondText("Not Found", status = HttpStatusCode.NotFound)
        call.respondBytes(data, contentType = contentType)
    }
}
