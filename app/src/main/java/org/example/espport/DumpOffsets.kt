package org.example.espport

/** Offsets from offsets_e_rva.hpp for the current ARM64 IL2CPP build. */
internal object DumpOffsets {
    const val GAME_CLASS_RVA = 0xA872380L
    /**
     * Globals observed in the current GameFacade initialization sequence.
     *
     * The first three are the TypeInfo-slot candidates found from managed
     * references.  The latter three are the values passed to the shared
     * class-initialization helper (the `off_*[0]` arguments in the native
     * pseudocode).  Builds differ in which one is populated first, so keep
     * all of them in the probe order.
     */
    val GAME_CLASS_RVA_CANDIDATES = longArrayOf(
        0xA872380L,
        0xA86EDB0L,
        0xA86D278L,
    )
    /** InitBase-style globals used by the external VZX data loop. */
    val GAME_INITBASE_RVA_CANDIDATES = longArrayOf(
        0xA86E5B8L,
        0xA86E380L,
        0xA988320L,
        0xA342EFCL,
        0xABFF6E0L,
        0xA9870BCL,
        0xAD6DC18L,
    )
    const val GAME_FACADE_CURRENT_MATCH_RVA = 0x75F0AE8L
    const val GAME_CURRENT_GAME = 0x8L
    const val GAME_MATCH = 0x90L
    const val MATCH_LOCAL_PLAYER = 0xD8L
    const val MATCH_ENTITIES = 0x128L
    const val MATCH_PLAYER_LIST = 0x158L
    const val IL2CPP_STATIC_FIELDS = 0xB8L
    const val LEGACY_STATIC_CLASS = 0x5CL
    const val LEGACY_CURRENT_MATCH = 0x50L
    const val LIST_ITEMS = 0x10L
    const val LIST_SIZE = 0x18L
    const val ARRAY_DATA = 0x20L
    const val PLAYER_HEAD = 0x6A8L
    const val PLAYER_RIGHT_FOOT = 0x6F8L
    const val TRANSFORM_NATIVE = 0x10L
    const val TRANSFORM_MATRIX = 0x38L
    const val TRANSFORM_INDEX = 0x40L
    const val TRANSFORM_MATRIX_LIST = 0x18L
    const val TRANSFORM_MATRIX_INDICES = 0x20L
    const val TRANSFORM_MATRIX_STRIDE = 0x30L
    const val CAMERA_MANAGER = 0xA8L
    const val CAMERA_MANAGER_CAMERA = 0x20L
    const val CAMERA_NATIVE = 0x10L
    const val CAMERA_MATRIX = 0xD8L
    const val CAMERA_MANAGED_CAMERA = 0x30L
    const val MANAGED_NATIVE_POINTER = 0x10L
}
