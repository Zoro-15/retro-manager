/**
 * Minimal single-pass binary XML reader/writer for the in-place fast path
 * (issue #45). Understands only the chunk skeleton required to locate the
 * string pool, the manifest/application/activity elements, and their string
 * or integer typed attributes — no DOM tree is materialized.
 */
internal object AxmlInPlace {

    class UnsupportedAxlLayout : Exception()

    const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    const val TYPE_STRING: Int = 0x03
    const val TYPE_INT: Int = 0x10
    const val TYPE_BOOLEAN: Int = 0x12

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val UTF8_FLAG = 0x00000100

    class StringPool(
        val bytes: ByteArray,
        val chunkStart: Int,
        val stringDataStart: Int, // absolute offset of the first string entry
        val offsets: IntArray,    // absolute offset of each string entry
        val utf8: Boolean
    )

    class Attr(
        val isAndroidNs: Boolean,
        val isGlobalNs: Boolean,
        val name: String,
        val dataType: Int,
        val data: Int,
        val dataOffset: Int // absolute offset of the typed value's 4-byte data field
    )

    class Element(
        val name: String,
        val attrs: List<Attr>,
        val children: MutableList<Element> = mutableListOf()
    ) {
        fun attrAndroid(name: String): Attr? = attrs.firstOrNull { it.isAndroidNs && it.name == name }

        fun attrGlobal(name: String): Attr? = attrs.firstOrNull { it.isGlobalNs && it.name == name }

        fun attrStringAndroid(name: String, pool: StringPool): String? =
            attrAndroid(name)?.let { if (it.dataType == TYPE_STRING) readString(pool, it.data) else null }
    }

    class Doc(
        val bytes: ByteArray,
        val pool: StringPool,
        val manifest: Element,
        val application: Element,
        val activities: List<Element>
    )

    /* ---------------------------- parsing ---------------------------- */

    /**
     * Parses the AXML skeleton, or returns `null` when the layout uses
     * constructs the fast path does not model (styled pools, exotic chunk
     * order, missing elements) — the caller falls back to structured mutation.
     */
    fun parse(bytes: ByteArray): Doc? {
        if (bytes.size < 8) return null
        if (u16(bytes, 0) != RES_XML_TYPE) return null

        val poolStart = 8
        if (u16(bytes, poolStart) != RES_STRING_POOL_TYPE) return null
        val pool = parsePool(bytes, poolStart) ?: return null

        // Resolve the android namespace index: aapt always emits the URI as a
        // string pool entry (referenced by xmlns declarations).
        val androidNsIdx = pool.indexOf(ANDROID_NS_URI)

        var offset = align4(poolStart + i32(bytes, poolStart + 4))
        val stack = ArrayDeque<Element>()
        var manifest: Element? = null
        var application: Element? = null
        val activities = mutableListOf<Element>()
        var closedTopLevel = false

        while (offset + 8 <= bytes.size && !closedTopLevel) {
            val type = u16(bytes, offset)
            val chunkSize = i32(bytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) return null

            when (type) {
                RES_XML_START_ELEMENT_TYPE -> {
                    val el = parseStartElement(bytes, offset, pool, androidNsIdx) ?: return null
                    if (manifest == null) {
                        manifest = el
                    }
                    stack.addLast(el)
                }
                RES_XML_END_ELEMENT_TYPE -> {
                    val el = stack.removeLastOrNull() ?: return null
                    if (stack.isEmpty()) {
                        closedTopLevel = true
                    } else {
                        val parent = stack.last()
                        parent.children.add(el)
                        if (el.name == "application" && parent.name == "manifest") {
                            application = el
                        }
                        if (el.name == "activity") {
                            activities.add(el)
                        }
                    }
                }
                else -> Unit // namespaces, resource map, comments: irrelevant
            }
            offset = align4(offset + chunkSize)
        }

        val m = manifest ?: return null
        val app = application ?: return null
        if (activities.isEmpty()) return null
        return Doc(bytes, pool, m, app, activities)
    }

    private fun parseStartElement(bytes: ByteArray, offset: Int, pool: StringPool, androidNsIdx: Int): Element? {
        // header: type u16, headerSize u16, size u32, line u32, comment u32
        // body:  ns u32, name u32, attributeStart u16, attributeSize u16,
        //        attributeCount u16, idIndex u16, classIndex u16, styleIndex u16
        val base = offset + 16
        if (base + 16 > bytes.size) return null
        val nameIdx = i32(bytes, base + 4)
        val attributeStart = u16(bytes, base + 8)
        val attributeSize = u16(bytes, base + 10)
        val attributeCount = u16(bytes, base + 12)
        val name = readString(pool, nameIdx) ?: return null

        val attrs = mutableListOf<Attr>()
        if (attributeSize >= 20) {
            for (i in 0 until attributeCount) {
                val a = base + attributeStart + i * attributeSize
                if (a + 20 > bytes.size) return null
                val ns = i32(bytes, a)
                val attrNameIdx = i32(bytes, a + 4)
                // rawValue u32 (+8); typedValue: size u16 (+12), res0 u8 (+14),
                // dataType u8 (+15), data u32 (+16)
                val dataType = bytes[a + 15].toInt() and 0xFF
                val data = i32(bytes, a + 16)
                val attrName = readString(pool, attrNameIdx) ?: return null
                attrs.add(
                    Attr(
                        isAndroidNs = androidNsIdx >= 0 && ns == androidNsIdx,
                        isGlobalNs = ns == -1 || ns == -2,
                        name = attrName,
                        dataType = dataType,
                        data = data,
                        dataOffset = a + 16
                    )
                )
            }
        }
        return Element(name, attrs)
    }

    private fun parsePool(bytes: ByteArray, start: Int): StringPool? {
        if (start + 28 > bytes.size) return null
        val headerSize = u16(bytes, start + 2)
        val stringCount = i32(bytes, start + 8)
        val styleCount = i32(bytes, start + 12)
        val flags = i32(bytes, start + 16)
        val stringsStart = i32(bytes, start + 20)
        if (stringCount < 0 || stringCount > 65536 || styleCount != 0 || headerSize < 28) return null
        if (stringsStart <= 0 || start + stringsStart > bytes.size) return null

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            val off = i32(bytes, start + headerSize + i * 4)
            if (off < 0) return null
            offsets[i] = start + stringsStart + off
        }
        return StringPool(
            bytes,
            chunkStart = start,
            stringDataStart = start + stringsStart,
            offsets = offsets,
            utf8 = (flags and UTF8_FLAG) != 0
        )
    }

    /* --------------------------- string io --------------------------- */

    fun readString(pool: StringPool, slot: Int): String? {
        if (slot < 0 || slot >= pool.offsets.size) return null
        val p = pool.offsets[slot]
        val bytes = pool.bytes
        return try {
            if (pool.utf8) {
                var q = p
                var charLen = bytes[q].toInt() and 0xFF
                q += 1
                if (charLen and 0x80 != 0) {
                    charLen = ((charLen and 0x7F) shl 8) or (bytes[q].toInt() and 0xFF)
                    q += 1
                }
                var byteLen = bytes[q].toInt() and 0xFF
                q += 1
                if (byteLen and 0x80 != 0) {
                    byteLen = ((byteLen and 0x7F) shl 8) or (bytes[q].toInt() and 0xFF)
                    q += 1
                }
                if (charLen < 0 || byteLen < 0 || q + byteLen > bytes.size) return null
                String(bytes, q, byteLen, Charsets.UTF_8)
            } else {
                var charLen = u16(bytes, p)
                var q = p + 2
                if (charLen and 0x8000 != 0) {
                    val hi = charLen and 0x7FFF
                    charLen = (hi shl 16) or u16(bytes, q)
                    q += 2
                }
                if (charLen < 0 || q + charLen * 2 > bytes.size) return null
                val sb = StringBuilder(charLen)
                for (i in 0 until charLen) {
                    sb.append(u16(bytes, q + i * 2).toChar())
                }
                sb.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun StringPool.indexOf(value: String): Int {
        for (i in offsets.indices) {
            if (readString(this, i) == value) return i
        }
        return -1
    }

    /**
     * True when [newValue] can replace the string at [slot] without growing
     * the pool: strings are self-delimiting (length prefix + terminator), so
     * an equal-or-shorter payload simply leaves inert padding before the next
     * slot's offset.
     */
    fun canPatchInPlace(pool: StringPool, slot: Int, newValue: String): Boolean {
        if (slot < 0 || slot >= pool.offsets.size) return false
        val p = pool.offsets[slot]
        val bytes = pool.bytes
        return try {
            if (pool.utf8) {
                var q = p
                var charLen = bytes[q].toInt() and 0xFF
                q += 1
                if (charLen and 0x80 != 0) q += 1
                var byteLen = bytes[q].toInt() and 0xFF
                q += 1
                if (byteLen and 0x80 != 0) q += 1
                newValue.toByteArray(Charsets.UTF_8).size <= byteLen
            } else {
                // Non-extended UTF-16 length; extended (0x8000-flagged) old
                // lengths mean a huge slot that any realistic new value fits.
                var oldChars = u16(bytes, p)
                if (oldChars and 0x8000 != 0) {
                    oldChars = ((oldChars and 0x7FFF) shl 16) or u16(bytes, p + 2)
                }
                newValue.length <= oldChars && newValue.length <= 0x7FFF
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rewrites the string at [slot] in place. Callers must have verified
     * [canPatchInPlace]. The trailing region of the old payload becomes
     * unreachable padding (nothing references it: the next slot has its own
     * offset and lengths are self-declared).
     */
    fun patchString(pool: StringPool, slot: Int, newValue: String) {
        val p = pool.offsets[slot]
        val bytes = pool.bytes
        if (pool.utf8) {
            val encoded = newValue.toByteArray(Charsets.UTF_8)
            var q = p
            q = writeLen(bytes, q, newValue.length)
            q = writeLen(bytes, q, encoded.size)
            encoded.copyInto(bytes, q)
            bytes[q + encoded.size] = 0
        } else {
            require(newValue.length <= 0x7FFF) { "String too long for in-place UTF-16 patch" }
            u16Store(bytes, p, newValue.length)
            var q = p + 2
            for (ch in newValue) {
                u16Store(bytes, q, ch.code)
                q += 2
            }
            u16Store(bytes, q, 0)
        }
    }

    private fun writeLen(bytes: ByteArray, at: Int, value: Int): Int {
        return if (value < 0x80) {
            bytes[at] = value.toByte()
            at + 1
        } else {
            require(value < 0x8000)
            bytes[at] = (0x80 or (value shr 8)).toByte()
            bytes[at + 1] = (value and 0xFF).toByte()
            at + 2
        }
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u16Store(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun i32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun align4(v: Int): Int = (v + 3) and (Int.MAX_VALUE - 3)
}
