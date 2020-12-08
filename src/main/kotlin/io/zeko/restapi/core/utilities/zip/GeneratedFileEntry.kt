package io.zeko.restapi.core.utilities.zip

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import java.io.ByteArrayInputStream
import java.io.InputStream


class GeneratedFileEntry(
    private val fileName: String,
    private val content: String
) : FileEntry {
    override val path: String
        get() = fileName

    override fun open(handler: Handler<AsyncResult<InputStream>>) {
        val buf = Buffer.buffer(content)
        val bis: InputStream = ByteArrayInputStream(buf.bytes)
        handler!!.handle(Future.succeededFuture(bis))
    }
}
