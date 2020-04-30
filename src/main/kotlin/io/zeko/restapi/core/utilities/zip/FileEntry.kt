package io.zeko.restapi.core.utilities.zip

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import java.io.InputStream

interface FileEntry {
    val path: String?

    fun open(handler: Handler<AsyncResult<InputStream>>)
}
