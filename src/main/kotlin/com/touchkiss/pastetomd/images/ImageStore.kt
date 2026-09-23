package com.touchkiss.pastetomd.images

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class ImageStore(directory: Path) {
    private val directory = Files.createDirectories(directory).toRealPath()
    private val lock = locks.computeIfAbsent(this.directory) { Any() }
    private val mapper = ObjectMapper()
    private val index get() = directory.resolve(MAP_FILE)

    fun save(bytes: ByteArray, extension: String): Path = synchronized(lock) {
        val hash = fingerprint(bytes)
        val entries = readMap()
        entries[hash]?.let { relative ->
            val existing = directory.resolve(relative).normalize()
            if (existing.startsWith(directory) && Files.isRegularFile(existing) &&
                fingerprint(Files.readAllBytes(existing)) == hash) return@synchronized existing
        }
        val target = directory.resolve("$hash.$extension")
        if (!Files.exists(target) || fingerprint(Files.readAllBytes(target)) != hash) {
            atomicWrite(target, bytes)
        }
        entries[hash] = directory.relativize(target).toString().replace('\\', '/')
        atomicWrite(index, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entries))
        target
    }

    fun rebuild(checkCanceled: () -> Unit = {}): Int = synchronized(lock) {
        val entries = sortedMapOf<String, String>()
        Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                .filter { it != index && !it.fileName.toString().startsWith(TEMP_PREFIX) }
                .sorted().forEach { file ->
                    checkCanceled()
                    val digest = MessageDigest.getInstance("SHA-256")
                    Files.newInputStream(file).use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            checkCanceled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    entries.putIfAbsent(hex(digest.digest()), directory.relativize(file).toString().replace('\\', '/'))
                }
        }
        checkCanceled()
        atomicWrite(index, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entries))
        entries.size
    }

    private fun readMap(): MutableMap<String, String> {
        if (!Files.exists(index)) return sortedMapOf()
        return mapper.readValue(index.toFile(), object : TypeReference<MutableMap<String, String>>() {})
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(directory, TEMP_PREFIX, ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        const val MAP_FILE = ".paste-to-md-image-map.json"
        private const val TEMP_PREFIX = ".paste-to-md-download-"
        private val locks = ConcurrentHashMap<Path, Any>()
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        fun fingerprint(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        fun resolveDirectory(value: String, projectPath: String?): Path {
            require(value.isNotBlank()) { "图片存放目录不能为空" }
            val expanded = if (value.contains("\${ProjectPath}")) {
                require(!projectPath.isNullOrBlank()) { "当前项目没有项目根路径" }
                value.replace("\${ProjectPath}", projectPath)
            } else value
            val path = Path.of(expanded)
            if (path.isAbsolute) return path.normalize()
            require(!projectPath.isNullOrBlank()) { "相对目录需要项目根路径" }
            return Path.of(projectPath).resolve(path).toAbsolutePath().normalize()
        }
    }
}
