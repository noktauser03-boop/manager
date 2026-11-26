package com.eren.dualmanager

import com.android.apksig.ApkSigner
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class Item(val name: String, val isDir: Boolean, val path: String, val size: Long)

private class Counting(o: OutputStream) : FilterOutputStream(o) {
    var n = 0L
    override fun write(b: Int) { out.write(b); n++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); n += len }
}

object ZipEditor {

    fun list(apk: File, prefix: String): List<Item> {
        val dirs = sortedMapOf<String, Item>()
        val files = sortedMapOf<String, Item>()
        ZipFile(apk).use { z ->
            for (e in z.entries()) {
                val n = e.name
                if (!n.startsWith(prefix) || n == prefix) continue
                val rest = n.substring(prefix.length)
                val slash = rest.indexOf('/')
                if (slash < 0) files[rest] = Item(rest, false, n, e.size)
                else {
                    val d = rest.substring(0, slash)
                    dirs[d] = Item(d, true, prefix + d + "/", 0)
                }
            }
        }
        return dirs.values + files.values
    }

    // path: dosya adı ya da "klasor/" ; inner: şu an bulunulan klasör öneki
    fun extract(apk: File, path: String, inner: String, dest: File) {
        val destCanon = dest.canonicalPath
        ZipFile(apk).use { z ->
            for (e in z.entries()) {
                val n = e.name
                val match = if (path.endsWith("/")) n.startsWith(path) else n == path
                if (!match) continue
                val out = File(dest, n.substring(inner.length))
                if (!out.canonicalPath.startsWith(destCanon)) continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
            }
        }
    }

    fun add(apk: File, inner: String, src: File) {
        val map = LinkedHashMap<String, File>()
        if (src.isDirectory) {
            src.walkTopDown().filter { it.isFile }.forEach {
                map[inner + src.name + "/" + it.relativeTo(src).path.replace('\\', '/')] = it
            }
        } else map[inner + src.name] = src
        rewrite(apk, { false }, map)
    }

    fun remove(apk: File, path: String) {
        rewrite(apk, { if (path.endsWith("/")) it.startsWith(path) else it == path }, emptyMap())
    }

    private fun isOldSignature(n: String): Boolean {
        if (n == "META-INF/MANIFEST.MF") return true
        if (!n.startsWith("META-INF/") || n.count { it == '/' } != 1) return false
        return n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")
    }

    private fun rewrite(apk: File, remove: (String) -> Boolean, add: Map<String, File>) {
        val tmp = File(apk.parentFile, apk.name + ".tmp")
        val cos = Counting(BufferedOutputStream(FileOutputStream(tmp)))
        val zos = ZipOutputStream(cos)
        ZipFile(apk).use { z ->
            for (e in z.entries()) {
                val n = e.name
                if (remove(n) || add.containsKey(n) || isOldSignature(n)) continue
                val ne = ZipEntry(n)
                if (e.method == ZipEntry.STORED) {
                    ne.method = ZipEntry.STORED
                    ne.size = e.size
                    ne.compressedSize = e.size
                    ne.crc = e.crc
                    // zipalign benzeri 4 bayt hizalama
                    val base = cos.n + 30 + n.toByteArray(Charsets.UTF_8).size
                    val p = ((4 - base % 4) % 4).toInt()
                    val extra = ByteArray(4 + p)
                    extra[0] = 0x35; extra[1] = 0xD9.toByte(); extra[2] = p.toByte()
                    ne.extra = extra
                } else ne.method = ZipEntry.DEFLATED
                zos.putNextEntry(ne)
                if (!e.isDirectory) z.getInputStream(e).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        for ((name, f) in add) {
            val ne = ZipEntry(name)
            ne.method = ZipEntry.DEFLATED
            zos.putNextEntry(ne)
            f.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        zos.close()
        apk.delete()
        tmp.renameTo(apk)
    }

    private fun loadStore(f: File, pass: String): KeyStore {
        var last: Exception? = null
        for (type in listOf("PKCS12", "BKS")) {
            try {
                val ks = KeyStore.getInstance(type)
                f.inputStream().use { ks.load(it, pass.toCharArray()) }
                return ks
            } catch (e: Exception) { last = e }
        }
        throw Exception("Anahtar açılamadı (PKCS12 veya BKS olmalı): ${last?.message}")
    }

    fun sign(input: File, output: File, ks: File, alias: String, pass: String) {
        val store = loadStore(ks, pass)
        val key = store.getKey(alias, pass.toCharArray()) as? PrivateKey
            ?: throw Exception("Alias bulunamadı: $alias")
        val chain = store.getCertificateChain(alias).map { it as X509Certificate }
        val cfg = ApkSigner.SignerConfig.Builder("CERT", key, chain).build()
        output.delete()
        ApkSigner.Builder(listOf(cfg))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }
}
