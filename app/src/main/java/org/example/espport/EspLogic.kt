package org.example.espport

import kotlin.math.abs
import kotlin.math.sqrt

/** Plain input records for the ESP pipeline; callers supply data without this module reading memory. */
data class Vec3(val x: Float, val y: Float, val z: Float)
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float)
data class TransformNode(
    val position: Vec3,
    val rotation: Quaternion = Quaternion(0f, 0f, 0f, 1f),
    val scale: Vec3 = Vec3(1f, 1f, 1f),
    val parentIndex: Int = -1,
)
data class PlayerSnapshot(
    val id: Long,
    val teamId: Int,
    val name: String,
    val currentHealth: Int,
    val maxHealth: Int,
    val headTransform: Int,
    val rightToeTransform: Int,
)
data class EspSnapshot(
    val localTeamId: Int,
    val localPosition: Vec3,
    val viewMatrix: FloatArray,
    val transforms: List<TransformNode>,
    val players: List<PlayerSnapshot>,
)
data class ScreenPoint(val x: Float, val y: Float)
data class EspBox(
    val playerId: Long,
    val name: String,
    val currentHealth: Int,
    val maxHealth: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val distance: Float,
)

/** Ports the source repo's transform, projection, team, name, HP, and range calculations. */
object EspLogic {
    const val MAX_DISTANCE = 220f
    private const val MIN_CLIP_W = 0.5f

    fun buildBoxes(snapshot: EspSnapshot, screenWidth: Float, screenHeight: Float): List<EspBox> {
        if (snapshot.viewMatrix.size < 16 || screenWidth <= 0f || screenHeight <= 0f) return emptyList()
        return snapshot.players.mapNotNull { player ->
            if (player.name.isBlank() || player.teamId == snapshot.localTeamId) return@mapNotNull null
            val head = worldPosition(snapshot.transforms, player.headTransform) ?: return@mapNotNull null
            val headRaised = head.copy(y = head.y + 0.2f)
            val feet = worldPosition(snapshot.transforms, player.rightToeTransform) ?: return@mapNotNull null
            val range = distance(snapshot.localPosition, headRaised)
            if (range > MAX_DISTANCE) return@mapNotNull null
            val headScreen = worldToScreen(headRaised, snapshot.viewMatrix, screenWidth, screenHeight)
            val feetScreen = worldToScreen(feet, snapshot.viewMatrix, screenWidth, screenHeight)
            val boxHeight = abs(headScreen.y - feetScreen.y)
            if (boxHeight < 2f) return@mapNotNull null
            val boxWidth = boxHeight * 0.5f
            EspBox(
                playerId = player.id,
                name = player.name,
                currentHealth = player.currentHealth,
                maxHealth = player.maxHealth,
                x = headScreen.x - boxWidth / 2f,
                y = minOf(headScreen.y, feetScreen.y),
                width = boxWidth,
                height = boxHeight,
                distance = range,
            )
        }
    }

    /** Resolves a Unity-style transform node and all of its parent transforms. */
    fun worldPosition(nodes: List<TransformNode>, nodeIndex: Int): Vec3? {
        if (nodeIndex !in nodes.indices) return null
        var result = nodes[nodeIndex].position
        var parentIndex = nodes[nodeIndex].parentIndex
        var steps = 0
        while (parentIndex >= 0) {
            if (parentIndex !in nodes.indices || ++steps > nodes.size) return null // invalid chain or cycle
            val parent = nodes[parentIndex]
            val scaled = Vec3(
                result.x * parent.scale.x,
                result.y * parent.scale.y,
                result.z * parent.scale.z,
            )
            result = parent.position + rotate(scaled, parent.rotation)
            parentIndex = parent.parentIndex
        }
        return result
    }

    /** Column-major matrix projection, matching the source's Unity WorldToScreen formula. */
    fun worldToScreen(point: Vec3, matrix: FloatArray, screenWidth: Float, screenHeight: Float): ScreenPoint {
        val w = maxOf(
            matrix[3] * point.x + matrix[7] * point.y + matrix[11] * point.z + matrix[15],
            MIN_CLIP_W,
        )
        val x = screenWidth / 2f +
            (matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12]) / w * screenWidth / 2f
        val y = screenHeight / 2f -
            (matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13]) / w * screenHeight / 2f
        return ScreenPoint(x, y)
    }

    private fun rotate(v: Vec3, q: Quaternion): Vec3 {
        // Equivalent to q * v * inverse(q), expressed without temporary quaternions.
        val tx = 2f * (q.y * v.z - q.z * v.y)
        val ty = 2f * (q.z * v.x - q.x * v.z)
        val tz = 2f * (q.x * v.y - q.y * v.x)
        return Vec3(
            v.x + q.w * tx + q.y * tz - q.z * ty,
            v.y + q.w * ty + q.z * tx - q.x * tz,
            v.z + q.w * tz + q.x * ty - q.y * tx,
        )
    }

    private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    private fun distance(a: Vec3, b: Vec3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
