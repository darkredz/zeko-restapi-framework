/*
 * Port of Java code from dmetzler
 */
package io.zeko.restapi.core.utilities.zip

import io.vertx.core.*
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import org.slf4j.LoggerFactory
import io.vertx.core.streams.Pump
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipGenerator(private val vertx: Vertx, source: FileEntryIterator) : ReadStream<Buffer> {
    /**
     * The current state.
     */
    @Volatile
    private var state: Int = STATUS_ACTIVE

    // Context in which we are executing
    private var context: Context? = null

    // Stream where we write the zip entries
    private val zos: ZipOutputStream

    // Stream from where we read compressed data
    private val pis: PipedInputStream

    // Intermediate stream to read each fileEntry
    private var fileEntryIS: InputStream? = null

    // List of ReadStream Handlers
    private var failureHandler: Handler<Throwable>? = null
    private var dataHandler: Handler<Buffer>? = null
    private var closeHandler: Handler<Void>? = null

    // The source of files
    private val source: FileEntryIterator

    // Size of the chunk we are sending / reading
    private val CHUNK_SIZE = 8092

    /**
     * Entry point to start the reading process.
     */
    private fun doRead() {
        acquireContext()
        if (state == STATUS_ACTIVE) {
            vertx.executeBlocking(Handler { promise: Promise<Any?> ->
                // Flushing the pipe has to happen regulary to to
                // block it
                next(Runnable { doFlushPipe() })

                // We start by reading the first file
                next(Runnable { doReadFile() })
                promise.complete()
            }, noop())
        }
    }

    /**
     * Reads the next file if available and pass it to the doReadBuffer at next tick. If no more file is available, we
     * close and stop the generator at next tick.
     */
    private fun doReadFile() {
        if (hasNextFile()) {
            readFile(source.next() as FileEntry, Handler { ar: AsyncResult<Void?> ->
                if (ar.succeeded()) {
                    next(Runnable { doReadFile() })
                } else {
                    handleError(ar.cause())
                }
            })
        } else {
            next(Runnable { doCloseAndStop() })
        }
    }

    private fun doCloseAndStop() {
        vertx.executeBlocking({ v: Promise<Any?> ->
            try {
                doFlushPipe()
                zos.close()
                v.complete()
            } catch (e: IOException) {
                v.fail(e)
            }
        }) { v: AsyncResult<Any?> ->
            if (v.succeeded()) {
                doCloseGenerator(closeHandler)
            } else {
                handleError(v.cause())
            }
        }
    }

    private fun readFile(entry: FileEntry, handler: Handler<AsyncResult<Void?>>) {
        vertx.executeBlocking(Handler { promise: Promise<Any?>? ->
            try {
                // Open the inputstream if needed
                if (fileEntryIS == null) {
                    openStreamForEntry(entry, handler)
                } else {
                    readStreamForEntry(entry, handler)
                }
            } catch (e: IOException) {
                handler.handle(Future.failedFuture(e))
            }
        }, noop())
    }

    /**
     * Read the InputStream recursively and notifies the handler when finished success.
     *
     * @param entry The entry to read
     * @param handler
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun readStreamForEntry(entry: FileEntry, handler: Handler<AsyncResult<Void?>>) {
        // Read a chunk and push to ZIP stream
        var bytesRead = 0
        var c = 0
        val buf = Buffer.buffer()
        while (fileEntryIS!!.read().also { c = it } != -1 && bytesRead < CHUNK_SIZE) {
            buf.appendByte(c.toByte())
            bytesRead++
        }
        // Here it can block because the pipe may be full (hence wrapping in
        // executeBlocking)
        zos.write(buf.getBytes(0, bytesRead))
        if (bytesRead == CHUNK_SIZE) {
            // We are not finished so recurse
            vertx.runOnContext { v: Void? -> readFile(entry, handler) }
        } else {
            // Handle end of file read
            fileEntryIS!!.close()
            fileEntryIS = null
            handler.handle(Future.succeededFuture())
        }
    }

    /**
     * Open an InputStream for the given file entry and call [ZipGenerator.readFile] on
     * Success.
     *
     * @param entry the file entry
     * @param handler the handler that is passed to readFile
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun openStreamForEntry(entry: FileEntry, handler: Handler<AsyncResult<Void?>>) {
        val e = ZipEntry(entry.path)
        zos.putNextEntry(e)
        entry.open(Handler { ar ->
            if (ar.succeeded()) {
                fileEntryIS = ar.result()
                vertx.runOnContext { readFile(entry, handler) }
            } else {
                handleError(ar.cause())
            }
        })
    }

    private fun hasNextFile(): Boolean {
        return source.hasNext()
    }

    private fun doFlushPipe() {
        acquireContext()
        try {
            if (state == STATUS_ACTIVE) {
                if (pis.available() > 0) {
                    // Read all possible data from the pipe
                    val tmp = ByteArray(pis.available())
                    val readBytes = pis.read(tmp)
                    if (readBytes > 0) {
                        val buffer = ByteArray(readBytes)
                        System.arraycopy(tmp, 0, buffer, 0, readBytes)
                        dataHandler!!.handle(Buffer.buffer(buffer))
                    }
                    next(Runnable { doFlushPipe() })
                } else {
                    // If there is nothing to read, wait a bit until next flush.
                    next(Runnable { doFlushPipe() })
                }
            }
        } catch (e: IOException) {
            handleError(e)
        }
    }

    private fun next(f: Runnable) {
        context!!.runOnContext { v: Void? -> f.run() }
    }

    private fun acquireContext() {
        if (context == null) {
            context = vertx.orCreateContext
        }
    }

    private fun doCloseGenerator(handler: Handler<Void>?) {
        doCloseGenerator(handler, null)
    }

    private fun doCloseGenerator(handler: Handler<Void>?, e: Void?) {
        state = STATUS_CLOSED
        context!!.runOnContext { event: Void? ->
            handler?.handle(e)
        }
    }

    private fun handleError(cause: Throwable) {
        state = STATUS_CLOSED
        if (failureHandler != null) {
            LOG.error(cause.toString())
            failureHandler!!.handle(cause)
        } else {
            LOG.warn("No handler for error: $cause")
        }
    }

    override fun handler(handler: Handler<Buffer>): ReadStream<Buffer> {
        requireNotNull(handler) { "handler" }
        dataHandler = handler
        doRead()
        return this
    }

    override fun fetch(amount: Long): ReadStream<Buffer> {
        return this
    }


    /**
     * Pauses the reading.
     *
     * @return the current `AsyncInputStream`
     */
    override fun pause(): ReadStream<Buffer>? {
        if (state == STATUS_ACTIVE) {
            state = STATUS_PAUSED
        }
        return this
    }

    /**
     * Resumes the reading.
     *
     * @return the current `AsyncInputStream`
     */
    override fun resume(): ReadStream<Buffer>? {
        when (state) {
            STATUS_CLOSED -> throw IllegalStateException("Cannot resume, already closed")
            STATUS_PAUSED -> {
                state = STATUS_ACTIVE
                doRead()
            }
            else -> {
            }
        }
        return this
    }

    /**
     * Sets the failure handler.
     *
     * @param handler the failure handler.
     * @return the current [org.wisdom.framework.vertx.AsyncInputStream]
     */
    override fun exceptionHandler(handler: Handler<Throwable>): ReadStream<Buffer>? {
        failureHandler = handler
        return this
    }

    private fun noop(): Handler<AsyncResult<Any?>> {
        return Handler { v: AsyncResult<Any?>? -> }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ZipGenerator::class.java)

        /**
         * PAUSED state.
         */
        const val STATUS_PAUSED = 0

        /**
         * ACTIVE state.
         */
        const val STATUS_ACTIVE = 1

        /**
         * CLOSED state.
         */
        const val STATUS_CLOSED = 2

        @JvmStatic
        fun downloadZip(vertx: Vertx, context: RoutingContext, zipName: String, files: List<TempFile>, handler: Handler<Boolean>? = null) {
            val fileEntries = object : FileEntryIterator {
                private var index = 0
                override fun remove() {}
                override fun hasNext(): Boolean = index < files.size

                override fun next(): FileEntry? = if (hasNext()) {
                    GeneratedFileEntry(files[index].name, files[index++].content)
                } else {
                    throw NoSuchElementException()
                }
            }

            val zip = ZipGenerator(vertx, fileEntries)

            zip.endHandler {
                context.response().end()
                handler?.handle(true)
            }.exceptionHandler { err ->
                handler?.handle(false)
                throw err
            }

            context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/zip, application/octet-stream")
                .putHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$zipName.zip\"")

            Pump.pump(zip, context.response().setChunked(true)).start()
        }
    }

    /**
     * Creates a generator of ZIP files.
     *
     * @param vertx a vertx instance
     * @param engine the templating engine
     * @param size the number of file to include in the ZIP
     * @throws IOException
     */
    init {
        this.source = source
        val out = PipedOutputStream()
        zos = ZipOutputStream(out)
        pis = PipedInputStream(out, CHUNK_SIZE)
    }

    override fun endHandler(endHandler: Handler<Void>?): ReadStream<Buffer> {
        closeHandler = endHandler
        return this
    }
}
