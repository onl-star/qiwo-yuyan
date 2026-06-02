package com.yuyan.imemodule.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

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
    private val base: String = baseUrl.trimEnd('/')

    /** 确保远端根目录存在。 */
    suspend fun ensureRootAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val code = mkcol("")
            code in 200..299 || code == 405 // 405 = already exists → OK
        } catch (_: Exception) {
            false
        }
    }

    /** 确保目录路径存在（递归创建中间目录）。 */
    suspend fun ensureDirAsync(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val parts = remotePath.trim('/').split('/')
        var path = ""
        for (part in parts) {
            path += "/$part"
            try {
                val code = mkcol(path)
                if (code !in 200..299 && code != 405) return@withContext false
            } catch (_: Exception) {
                return@withContext false
            }
        }
        true
    }

    /** 上传文件。 */
    suspend fun putFileAsync(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureParentDir(remotePath)
            val url = URL("$base${encodePath(remotePath)}")
            val conn = openConnection(url, "PUT")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            FileInputStream(localFile).use { input ->
                conn.outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 下载文件。 */
    suspend fun getFileAsync(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            localFile.parentFile?.mkdirs()
            val url = URL("$base${encodePath(remotePath)}")
            val conn = openConnection(url, "GET")
            if (conn.responseCode == 404) {
                conn.disconnect()
                return@withContext false
            }
            conn.inputStream.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 获取文件字节内容。 */
    suspend fun getBytesAsync(remotePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$base${encodePath(remotePath)}")
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
            ensureParentDir(remotePath)
            val url = URL("$base${encodePath(remotePath)}")
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

    /** 删除远端文件。 */
    suspend fun deleteAsync(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$base${encodePath(remotePath)}")
            val conn = openConnection(url, "DELETE")
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /** 列出远端目录下的文件。返回文件名列表（不递归）。 */
    suspend fun listAsync(remotePath: String = ""): List<WebDavEntry> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$base${encodePath(remotePath)}")
            val conn = openConnection(url, "PROPFIND")
            conn.setRequestProperty("Depth", if (remotePath.isEmpty()) "1" else "1")
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

    private fun ensureParentDir(remotePath: String) {
        val parent = remotePath.trim('/').split('/').dropLast(1).joinToString("/")
        if (parent.isNotEmpty()) {
            mkcol("/$parent")
        }
    }

    private fun mkcol(path: String): Int {
        val url = URL("$base${encodePath(path)}")
        val conn = openConnection(url, "MKCOL")
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun openConnection(url: URL, method: String): HttpURLConnection {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "QiwoSync/1.0")
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

    private fun encodePath(path: String): String {
        return path.split('/').joinToString("/") { part ->
            // 简单编码，保留大多数字符
            part.replace(" ", "%20")
        }
    }

    /** 简易 PROPFIND XML 解析。 */
    private fun parsePropfind(xml: String, basePath: String): List<WebDavEntry> {
        val entries = mutableListOf<WebDavEntry>()
        val responseRegex = Regex(
            "<d:response>(.*?)</d:response>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val hrefRegex = Regex(
            "<d:href>(.*?)</d:href>",
            setOf(RegexOption.IGNORE_CASE)
        )
        val modifiedRegex = Regex(
            "<d:getlastmodified>(.*?)</d:getlastmodified>",
            setOf(RegexOption.IGNORE_CASE)
        )
        val sizeRegex = Regex(
            "<d:getcontentlength>(.*?)</d:getcontentlength>",
            setOf(RegexOption.IGNORE_CASE)
        )

        for (response in responseRegex.findAll(xml)) {
            val body = response.groupValues[1]
            val href = hrefRegex.find(body)?.groupValues?.get(1) ?: continue
            val name = href.trim().trimEnd('/').split('/').last()
            if (name.isEmpty()) continue
            val isDir = href.trim().endsWith("/")

            val modifiedStr = modifiedRegex.find(body)?.groupValues?.get(1)
            val lastModified = try {
                Instant.parse(modifiedStr?.replace(" ", "T")?.plus("Z") ?: "1970-01-01T00:00:00Z")
            } catch (_: Exception) {
                Instant.EPOCH
            }

            val size = sizeRegex.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            entries.add(
                WebDavEntry(
                    name = name,
                    path = if (basePath.isEmpty()) name else "$basePath/$name",
                    isDirectory = isDir,
                    sizeBytes = size,
                    lastModified = lastModified
                )
            )
        }
        return entries
    }
}

/** WebDAV 目录条目。 */
data class WebDavEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Instant
)
