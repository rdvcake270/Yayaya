package org.example.espport

import android.util.Base64
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Reads explicit byte ranges from an Android process through a KernelSU-provided `su`. */
class RootProcessMemoryReader(
    private val packageName: String = TARGET_PACKAGE,
    private val timeoutSeconds: Long = 5,
) {
    init {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
            "Invalid Android package name"
        }
        require(timeoutSeconds > 0)
    }

    /** Returns the first PID reported by `pidof`, or null if the app is not running. */
    fun findPid(): Int? {
        val output = runRoot("pidof $packageName")
        if (output.exitCode != 0) return null
        return output.stdout.trim().split(Regex("\\s+")).firstNotNullOfOrNull { it.toIntOrNull() }
    }

    /** Returns the first file-offset-zero mapping for a loaded native library. */
    fun findModuleBase(moduleName: String = "libil2cpp.so"): Long? {
        require(moduleName.matches(Regex("[A-Za-z0-9_.-]+\\.so"))) { "Invalid library name" }
        val pid = findPid() ?: return null
        val output = runRoot(
            "awk '\$3 == \"00000000\" && \$NF ~ /\\/$moduleName\$/ { split(\$1, r, \"-\"); print r[1]; exit }' /proc/$pid/maps",
        )
        if (output.exitCode != 0) throw IOException("Could not read $moduleName mapping")
        return output.stdout.trim().takeIf(String::isNotEmpty)?.toLongOrNull(16)
    }

    fun findMappingPermissions(address: Long): String? {
        val pid = findPid() ?: return null
        val maps = runRoot("cat /proc/$pid/maps")
        if (maps.exitCode != 0) throw IOException("Could not read target memory map")
        return maps.stdout.lineSequence().firstNotNullOfOrNull { line ->
            val columns = line.trim().split(Regex("\\s+"))
            val range = columns.firstOrNull()?.split('-', limit = 2) ?: return@firstNotNullOfOrNull null
            if (range.size != 2) return@firstNotNullOfOrNull null
            val start = range[0].toLongOrNull(16) ?: return@firstNotNullOfOrNull null
            val end = range[1].toLongOrNull(16) ?: return@firstNotNullOfOrNull null
            if (address in start until end) columns.getOrNull(1) else null
        }
    }

    /** Reads [length] bytes at [address], which may be decimal or `0x`-prefixed hexadecimal. */
    fun read(address: String, length: Int): ByteArray {
        require(length in 1..MAX_READ_SIZE) { "Read length must be between 1 and $MAX_READ_SIZE bytes" }
        val offset = parseAddress(address)
        val pid = findPid() ?: throw IOException("$packageName is not running")
        val output = runRoot(
            "dd if=/proc/$pid/mem bs=1 skip=$offset count=$length 2>/dev/null | base64 | tr -d '\\n'",
        )
        if (output.exitCode != 0) throw IOException("Root memory read failed (exit ${output.exitCode})")
        val bytes = try {
            Base64.decode(output.stdout, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IOException("Could not decode memory read", e)
        }
        if (bytes.size != length) throw IOException("Short read: received ${bytes.size} of $length bytes")
        return bytes
    }

    fun readInt32(address: String, littleEndian: Boolean = true): Int =
        read(address, Int.SIZE_BYTES).let { bytes ->
            val order = if (littleEndian) 3 downTo 0 else 0..3
            order.fold(0) { value, index -> (value shl 8) or (bytes[index].toInt() and 0xff) }
        }

    fun readInt64(address: String, littleEndian: Boolean = true): Long =
        read(address, Long.SIZE_BYTES).let { bytes ->
            val order = if (littleEndian) 7 downTo 0 else 0..7
            order.fold(0L) { value, index -> (value shl 8) or (bytes[index].toLong() and 0xffL) }
        }

    fun readPointer(address: Long): Long = readInt64("0x${address.toString(16)}")

    fun readFloat32(address: String, littleEndian: Boolean = true): Float =
        Float.fromBits(readInt32(address, littleEndian))

    fun readFloat32Array(address: Long, count: Int): FloatArray {
        require(count in 1..256)
        val bytes = read("0x${address.toString(16)}", count * Float.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.float }
    }

    /** Traces the offsets from offsets_e_rva.hpp against the Android process. */
    fun probeOffsets(): String {
        val lines = mutableListOf<String>()
        fun pointer(label: String, objectAddress: Long, offset: Long): Long {
            val slot = objectAddress + offset
            val value = readPointer(slot)
            lines += "$label [${hex(objectAddress)} + ${hex(offset)}] -> ${hex(value)}"
            if (value < 0x10000L) throw IOException("$label returned a null or implausible pointer")
            return value
        }

        try {
            val module = findModuleBase() ?: throw IOException("libil2cpp.so is not mapped (is the game running?)")
            lines += "PID ${findPid()}; libil2cpp.so base ${hex(module)}"
            val typeInfoSlot = module + DumpOffsets.GAME_CLASS_RVA
            val mapping = findMappingPermissions(typeInfoSlot)
            lines += "Game class RVA ${hex(DumpOffsets.GAME_CLASS_RVA)} is in mapping ${mapping ?: "unknown"}"
            if (mapping?.contains('x') == true) {
                lines += "Stopped: the game class RVA lands in executable memory, so it cannot be used as the TypeInfo pointer slot."
                return lines.joinToString("\n")
            }
            val typeInfo = readPointer(typeInfoSlot)
            lines += "Game class TypeInfo [${hex(typeInfoSlot)}] -> ${hex(typeInfo)}"
            if (typeInfo < 0x10000L) throw IOException("Game class TypeInfo returned a null or implausible pointer")
            val staticFields = pointer("GameFacade static fields", typeInfo, 0xB8)
            val game = pointer("Current game", staticFields, DumpOffsets.GAME_CURRENT_GAME)
            val match = pointer("Match", game, DumpOffsets.GAME_MATCH)
            pointer("Local player", match, DumpOffsets.MATCH_LOCAL_PLAYER)
            pointer("Entity collection", match, DumpOffsets.MATCH_ENTITIES)
            lines += "Camera/player subobject offsets available: managed camera ${hex(DumpOffsets.CAMERA_MANAGED_CAMERA)}, native pointer ${hex(DumpOffsets.MANAGED_NATIVE_POINTER)}"
        } catch (e: Exception) {
            lines += "Probe stopped: ${e.message}"
        }
        return lines.joinToString("\n")
    }

    private fun hex(value: Long) = "0x${value.toString(16)}"

    private fun runRoot(script: String): RootOutput {
        val process = try {
            ProcessBuilder("su", "-c", script).start()
        } catch (e: IOException) {
            throw IOException("Could not start su; grant root access to this app in KernelSU Next", e)
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val completed = try {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw IOException("Root command was interrupted", e)
        }
        if (!completed) {
            process.destroyForcibly()
            throw IOException("Root command timed out")
        }
        return RootOutput(stdout, process.exitValue())
    }

    private fun parseAddress(address: String): String {
        val value = address.trim()
        val parsed = try {
            if (value.startsWith("0x", ignoreCase = true)) BigInteger(value.substring(2), 16)
            else BigInteger(value, 10)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Address must be decimal or 0x-prefixed hexadecimal", e)
        }
        require(parsed.signum() >= 0 && parsed.bitLength() <= 63) { "Address is out of range" }
        return parsed.toString(10)
    }

    private data class RootOutput(val stdout: String, val exitCode: Int)

    companion object {
        const val TARGET_PACKAGE = "com.dts.freefiremax"
        const val MAX_READ_SIZE = 4096
    }
}
