package com.yuyan.imemodule.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * WebDAV HTTP 客户端。
 * 使用 HttpURLConnection 实现，零外部依赖。
 * 协议：PUT/GET/DELETE/PROPFIND/MKCOL，Basic Auth。
 */
class WebDavClient(
    private val baseUrl: String,
    private val username: String?,
    private val password: String?
) {
    companion object {
        private const val TAG = "QiwoWebDav"
    }

    private val base: String = baseUrl.trimEnd('/')

    /** 确保远端根目录存在（递归创建所有中间目录）。 */
    suspend fun ensureRootAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先尝试直接 MKCOL 根路径
            val code = mkcol("")
            if (code in 200..299 || code == 405) return@withContext true
            // 405 = already exists, 201/200 = created
            // 如果失败，尝试逐级创建
            val parts = baseUrl.trimEnd('/').split('/').drop(3) // skip https://host
            var path = ""
            for (part in parts) {
                if (part.isEmpty()) continue
                path += "/$part"
                val c = mkcol(path)
                Log.d(TAG, "MKCOL $path -> $c")
                if (c !in 200..299 && c != 405) {
                    Log.w(TAG, "Failed to create $path: HTTP $c")
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureRootAsync failed", e)
            false
        }
    }

    /** 上传文件。先确保父目录存在。 */
    suspend fun putFileAsync(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // 确保父目录存在
            val parent = remotePath.trim('/').split('/').dropLast(1).joinToString("/")
            if (parent.isNotEmpty() && !ensureDir(parent)) {
                Log.w(TAG, "Failed to create parent dir: $parent")
                return@withContext false
            }

            val url = URL("$base/${remotePath.split('/').joinToString("/") { encode(it) }}")
            val conn = openConnection(url, "PUT")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", localFile.length().toString())

            FileInputStream(localFile).use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output, 8192)
                }
            }

            val code = conn.responseCode
            val ok = code in 200..299
            if (!ok) {
                val errBody = try { conn.errorStream?.readBytes()?.toString(StandardCharsets.UTF_8) } catch (_: Exception) { null }
                Log.w(TAG, "PUT $remotePath -> HTTP $code ${errBody ?: ""}")
            } else {
                Log.d(TAG, "PUT $remotePath -> OK ($code)")
            }
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "PUT $remotePath failed", e)
            false
        }
    }

    /** 下载文件。 */
    suspend fun getFileAsync(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            localFile.parentFile?.mkdirs()
            val url = URL("$base/${remotePath.split('/').joinToString("/") { encode(it) }}")
            val conn = openConnection(url, "GET")
            if (conn.responseCode == 404) {
                conn.disconnect()
                return@withContext false
            }
            conn.inputStream.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }
            val code = conn.responseCode
            conn.disconnect()
            val ok = code in 200..299
            if (!ok) Log.w(TAG, "GET $remotePath -> HTTP $code")
            else Log.d(TAG, "GET $remotePath -> OK")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "GET $remotePath failed", e)
            false
        }
    }

    /** 获取文件字节内容。 */
    suspend fun getBytesAsync(remotePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$base/${remotePath.split('/').joinToString("/") { encode(it) }}")
            val conn = openConnection(url, "GET")
            if (conn.responseCode == 404) {
                conn.disconnect()
                return@withContext null
            }
            val result = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            result
        } catch (_: Exception) {
            null
        }
    }

    /** 上传字节内容。 */
    suspend fun putBytesAsync(remotePath: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = remotePath.trim('/').split('/').dropLast(1).joinToString("/")
            if (parent.isNotEmpty()) ensureDir(parent)

            val url = URL("$base/${remotePath.split('/').joinToString("/") { encode(it) }}")
            val conn = openConnection(url, "PUT")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /** 列出远端目录下的文件。 */
    suspend fun listAsync(remotePath: String = ""): List<WebDavEntry> = withContext(Dispatchers.IO) {
        try {
            val path = if (remotePath.isEmpty()) "" else "/${remotePath.split('/').joinToString("/") { encode(it) }}"
            val url = URL("$base$path")
            val conn = openConnection(url, "PROPFIND")
            conn.setRequestProperty("Depth", "1")
            conn.doOutput = true
            val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getlastmodified/>
    <d:getcontentlength/>
  </d:prop>
</d:propfind>"""
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode == 207) {
                val xml = conn.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                conn.disconnect()
                parsePropfind(xml, remotePath)
            } else {
                conn.disconnect()
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- 内部方法 ----

    /** 递归创建目录路径。 */
    private fun ensureDir(remotePath: String): Boolean {
        val parts = remotePath.trim('/').split('/')
        var path = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            path += "/$part"
            val code = mkcol(path)
            if (code !in 200..299 && code != 405) {
                Log.w(TAG, "MKCOL $path -> HTTP $code")
                return false
            }
        }
        return true
    }

    private fun mkcol(path: String): Int {
        return try {
            val url = URL("$base${if (path.isEmpty()) "" else "/${path.split('/').joinToString("/") { encode(it) }}"}")
            val conn = openConnection(url, "MKCOL")
            val code = conn.responseCode
            conn.disconnect()
            code
        } catch (e: Exception) {
            Log.e(TAG, "MKCOL $path failed", e)
            -1
        }
    }

    private fun openConnection(url: URL, method: String): HttpURLConnection {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "QiwoSync/1.0")
            // 处理重定向
            instanceFollowRedirects = true
            if (username != null && password != null) {
                val auth = android.util.Base64.encodeToString(
                    "$username:$password".toByteArray(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                setRequestProperty("Authorization", "Basic $auth")
            }
        }
        return conn
    }

    /** URL 编码（保留斜杠）。 */
    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
        .replace("+", "%20")
        .replace("%2F", "/")

    /** 简易 PROPFIND XML 解析。 */
    private fun parsePropfind(xml: String, basePath: String): List<WebDavEntry> {
        val entries = mutableListOf<WebDavEntry>()
        val responseRegex = Regex(
            "<d:response>(.*?)</d:response>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val hrefRegex = Regex("<d:href>(.*?)</d:href>", setOf(RegexOption.IGNORE_CASE))
        val modifiedRegex = Regex("<d:getlastmodified>(.*?)</d:getlastmodified>", setOf(RegexOption.IGNORE_CASE))
        val sizeRegex = Regex("<d:getcontentlength>(.*?)</d:getcontentlength>", setOf(RegexOption.IGNORE_CASE))

        for (response in responseRegex.findAll(xml)) {
            val body = response.groupValues[1]
            val href = hrefRegex.find(body)?.groupValues?.get(1) ?: continue
            val name = href.trim().trimEnd('/').split('/').last()
            if (name.isEmpty()) continue
            val isDir = href.trim().endsWith("/")

            val modifiedStr = modifiedRegex.find(body)?.groupValues?.get(1)
            val lastModified = try {
                Instant.parse(modifiedStr?.replace(" ", "T")?.plus("Z") ?: "1970-01-01T00:00:00Z")
            } catch (_: Exception) { Instant.EPOCH }

            val size = sizeRegex.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            entries.add(WebDavEntry(
                name = name,
                path = if (basePath.isEmpty()) name else "$basePath/$name",
                isDirectory = isDir,
                sizeBytes = size,
                lastModified = lastModified
            ))
        }
        return entries
    }
}

data class WebDavEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Instant
)
