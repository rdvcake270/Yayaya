package org.example.espport

data class EspFrame(
    val valid: Boolean = false,
    val status: String = "",
    val accepted: Int = 0,
    val boxes: List<EspFrameBox> = emptyList(),
)

data class EspFrameBox(
    val x: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val distance: Float,
    val bones: List<FloatArray> = emptyList(),
)

fun interface EspFrameProvider {
    fun readFrame(): EspFrame
}

/** Reads the live game-class pointer chain through `su`, without fabricating entity boxes. */
internal class RootDiagnosticFrameProvider(
    private val reader: RootProcessMemoryReader = RootProcessMemoryReader(),
) : EspFrameProvider {
    private var lastReadAt = 0L
    private var lastStatus = "Waiting for root-memory probe"

    override fun readFrame(): EspFrame {
        val now = System.currentTimeMillis()
        if (now - lastReadAt >= 500L) {
            lastReadAt = now
            lastStatus = reader.probeOffsets()
        }
        return EspFrame(valid = false, status = lastStatus)
    }
}

/** Minimal root-only ESP reader: player list, head/foot transforms, and camera matrix. */
internal class RootBoxFrameProvider(
    private val reader: RootProcessMemoryReader = RootProcessMemoryReader(),
) : EspFrameProvider {
    private var lastReadAt = 0L
    private var lastFrame = EspFrame(status = "Waiting for root-memory reader")

    override fun readFrame(): EspFrame {
        val now = System.currentTimeMillis()
        if (now - lastReadAt < 250L) return lastFrame
        lastReadAt = now
        lastFrame = runCatching { readFrameNow() }.getOrElse {
            EspFrame(status = "ESP read failed: ${it.message ?: "unknown error"}")
        }
        return lastFrame
    }

    private fun readFrameNow(): EspFrame {
        val module = reader.findModuleBase() ?: error("libil2cpp.so is not mapped")
        val typeInfo = reader.readPointer(module + DumpOffsets.GAME_CLASS_RVA)
        val staticFields = reader.readPointer(typeInfo + DumpOffsets.IL2CPP_STATIC_FIELDS)
        val game = reader.readPointer(staticFields + DumpOffsets.GAME_CURRENT_GAME)
        val match = reader.readPointer(game + DumpOffsets.GAME_MATCH)
        val localPlayer = reader.readPointer(match + DumpOffsets.MATCH_LOCAL_PLAYER)
        val cameraManager = reader.readPointer(game + DumpOffsets.CAMERA_MANAGER)
        val camera = reader.readPointer(cameraManager + DumpOffsets.CAMERA_MANAGER_CAMERA)
        val matrixNative = reader.readPointer(camera + DumpOffsets.CAMERA_NATIVE)
        val matrix = reader.readFloat32Array(matrixNative + DumpOffsets.CAMERA_MATRIX, 16)
        val list = reader.readPointer(match + DumpOffsets.MATCH_PLAYER_LIST)
        val items = reader.readPointer(list + DumpOffsets.LIST_ITEMS)
        val count = reader.readInt32("0x${(list + DumpOffsets.LIST_SIZE).toString(16)}")
            .coerceIn(0, 128)
        val boxes = ArrayList<EspFrameBox>()
        for (index in 0 until count) {
            val player = reader.readPointer(items + DumpOffsets.ARRAY_DATA + index * 8L)
            if (player == 0L || player == localPlayer) continue
            val head = worldPosition(reader, reader.readPointer(player + DumpOffsets.PLAYER_HEAD)) ?: continue
            val foot = worldPosition(reader, reader.readPointer(player + DumpOffsets.PLAYER_RIGHT_FOOT)) ?: continue
            val headScreen = worldToScreen(head.copy(y = head.y + 0.2f), matrix) ?: continue
            val footScreen = worldToScreen(foot, matrix) ?: continue
            val height = kotlin.math.abs(headScreen.second - footScreen.second)
            if (height < 0.01f || height > 2f) continue
            boxes += EspFrameBox(
                x = headScreen.first,
                top = minOf(headScreen.second, footScreen.second),
                width = height * 0.5f,
                height = height,
                name = "Entity",
                hp = 1,
                maxHp = 1,
                distance = 0f,
            )
        }
        return EspFrame(true, "ESP: ${boxes.size} boxes · root read-only", boxes.size, boxes)
    }

    private fun worldPosition(reader: RootProcessMemoryReader, node: Long): Vec3? {
        if (node == 0L) return null
        val transform = reader.readPointer(node + DumpOffsets.TRANSFORM_NATIVE)
        if (transform == 0L) return null
        val matrix = reader.readPointer(transform + DumpOffsets.TRANSFORM_MATRIX)
        val index = reader.readInt32("0x${(transform + DumpOffsets.TRANSFORM_INDEX).toString(16)}")
        if (matrix == 0L || index !in 0..100000) return null
        val matrixList = reader.readPointer(matrix + DumpOffsets.TRANSFORM_MATRIX_LIST)
        val matrixIndices = reader.readPointer(matrix + DumpOffsets.TRANSFORM_MATRIX_INDICES)
        if (matrixList == 0L || matrixIndices == 0L) return null
        val result = readMatrix(reader, matrixList + index * DumpOffsets.TRANSFORM_MATRIX_STRIDE)
        var parent = reader.readInt32("0x${(matrixIndices + index * 4L).toString(16)}")
        var steps = 0
        while (parent >= 0 && steps++ < 128) {
            val parentMatrix = readMatrix(reader, matrixList + parent * DumpOffsets.TRANSFORM_MATRIX_STRIDE)
            result.position = result.position + rotate(scale(result.position, parentMatrix.scale), parentMatrix.rotation)
            parent = reader.readInt32("0x${(matrixIndices + parent * 4L).toString(16)}")
        }
        return result.position
    }

    private data class TransformMatrix(var position: Vec3, val rotation: Quaternion, val scale: Vec3)

    private fun readMatrix(reader: RootProcessMemoryReader, address: Long): TransformMatrix {
        val values = reader.readFloat32Array(address, 12)
        return TransformMatrix(
            Vec3(values[0], values[1], values[2]),
            Quaternion(values[4], values[5], values[6], values[7]),
            Vec3(values[8], values[9], values[10]),
        )
    }

    private fun worldToScreen(point: Vec3, matrix: FloatArray): Pair<Float, Float>? {
        val w = matrix[3] * point.x + matrix[7] * point.y + matrix[11] * point.z + matrix[15]
        if (w <= 0.01f) return null
        val x = 0.5f + (matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12]) / w * 0.5f
        val y = 0.5f - (matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13]) / w * 0.5f
        return x to y
    }

    private fun scale(v: Vec3, s: Vec3) = Vec3(v.x * s.x, v.y * s.y, v.z * s.z)

    private fun rotate(v: Vec3, q: Quaternion): Vec3 {
        val tx = 2f * (q.y * v.z - q.z * v.y)
        val ty = 2f * (q.z * v.x - q.x * v.z)
        val tz = 2f * (q.x * v.y - q.y * v.x)
        return Vec3(v.x + q.w * tx + q.y * tz - q.z * ty, v.y + q.w * ty + q.z * tx - q.x * tz, v.z + q.w * tz + q.x * ty - q.y * tx)
    }
}

internal class SampleEspFrameProvider : EspFrameProvider {
    override fun readFrame(): EspFrame {
        val boxes = EspLogic.buildBoxes(EspSamples.snapshot(), 1_000f, 1_000f).map { box ->
            EspFrameBox(
                x = (box.x + box.width / 2f) / 1_000f,
                top = box.y / 1_000f,
                width = box.width / 1_000f,
                height = box.height / 1_000f,
                name = box.name,
                hp = box.currentHealth,
                maxHp = box.maxHealth,
                distance = box.distance,
            )
        }
        return EspFrame(true, "ESP: ${boxes.size} targets · read-only", boxes.size, boxes)
    }
}

internal object EspSamples {
    fun snapshot(): EspSnapshot {
        val transforms = mutableListOf<TransformNode>()
        val players = mutableListOf<PlayerSnapshot>()
        fun add(id: Long, name: String, team: Int, hp: Int, x: Float, halfHeight: Float) {
            val head = transforms.size
            transforms += TransformNode(Vec3(x, halfHeight, 0f))
            val feet = transforms.size
            transforms += TransformNode(Vec3(x, -halfHeight, 0f))
            players += PlayerSnapshot(id, team, name, hp, 100, head, feet)
        }
        add(1, "Player 1", 2, 82, -0.52f, 0.62f)
        add(2, "Player 2", 2, 46, 0.02f, 0.52f)
        add(3, "Player 3", 1, 100, 0.56f, 0.58f)
        return EspSnapshot(1, Vec3(0f, 0f, 0f), floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f,
        ), transforms, players)
    }
}
