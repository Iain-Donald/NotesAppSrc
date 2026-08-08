package com.liblens.xyznotes

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream

object TarManager {

    /*fun pack(files: Map<String, ByteArray>): ByteArray { // iain v1-v2
        val baos = ByteArrayOutputStream()
        TarArchiveOutputStream(baos).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            files.forEach { (path, content) ->
                val entry = TarArchiveEntry(path)
                entry.size = content.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }
        return baos.toByteArray()
    }*/

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