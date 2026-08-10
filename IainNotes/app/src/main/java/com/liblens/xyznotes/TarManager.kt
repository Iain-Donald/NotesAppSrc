package com.liblens.xyznotes

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream

object TarManager {

    fun unpack(tarBytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        TarArchiveInputStream(ByteArrayInputStream(tarBytes)).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = tar.readBytes()
                }
                entry = tar.nextEntry
            }
        }
        return result
    }
}