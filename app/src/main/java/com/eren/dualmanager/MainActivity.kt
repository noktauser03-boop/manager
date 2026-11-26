package com.eren.dualmanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

class Pane(var dir: File) {
    var archive: File? = null
    var inner = ""
    var selected: String? = null
    var items: List<Item> = emptyList()
    lateinit var list: ListView
    lateinit var header: TextView
}

class MainActivity : Activity() {
    private val panes = ArrayList<Pane>()
    private var active = 0
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        askPermission()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val start = Environment.getExternalStorageDirectory()
        for (i in 0..1) {
            val p = Pane(start)
            panes.add(p)
            p.header = TextView(this).apply { setPadding(16, 10, 16, 10); setTextColor(Color.WHITE); textSize = 13f }
            p.list = ListView(this)
            root.addView(p.header, LinearLayout.LayoutParams(-1, -2))
            root.addView(p.list, LinearLayout.LayoutParams(-1, 0, 1f))
            p.list.setOnItemClickListener { _, _, pos, _ -> active = i; onTap(p, p.items[pos]) }
            p.list.setOnItemLongClickListener { _, _, pos, _ -> active = i; onLong(p, p.items[pos]); true }
            p.header.setOnClickListener { active = i; refreshAll() }
        }
        val bar = LinearLayout(this)
        fun btn(t: String, f: () -> Unit) {
            val bt = Button(this)
            bt.text = t; bt.isAllCaps = false; bt.textSize = 12f
            bt.setOnClickListener { f() }
            bar.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
        }
        btn("Kopyala") { copy() }
        btn("Sil") { delete() }
        btn("Anahtar") { keyDialog() }
        btn("Yenile") { refreshAll() }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refreshAll() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { up(panes[active]); refreshAll() }

    private fun askPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager())
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun ui(f: () -> Unit) = runOnUiThread(f)

    private fun load(p: Pane): List<Item> {
        val a = p.archive
        if (a != null) return ZipEditor.list(a, p.inner)
        val files = p.dir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { Item(it.name, it.isDirectory, it.absolutePath, it.length()) }
    }

    private fun refreshAll() { panes.forEachIndexed { i, p -> refresh(p, i) } }

    private fun refresh(p: Pane, idx: Int) {
        p.items = try { listOf(Item("..", true, "..", 0)) + load(p) } catch (e: Exception) { toast("Hata: ${e.message}"); listOf(Item("..", true, "..", 0)) }
        val a = p.archive
        p.header.text = if (a != null) "${a.name}!/${p.inner}" else p.dir.absolutePath
        p.header.setBackgroundColor(Color.parseColor(if (idx == active) "#1976D2" else "#78909C"))
        p.list.adapter = object : ArrayAdapter<Item>(this, 0, p.items) {
            override fun getView(pos: Int, v: View?, parent: ViewGroup): View {
                val t = (v as? TextView) ?: TextView(context).apply { setPadding(20, 18, 20, 18); textSize = 15f }
                val item = getItem(pos)!!
                t.text = (if (item.isDir) "📁 " else "📄 ") + item.name +
                        (if (!item.isDir && item.size > 0) "  (${item.size / 1024} KB)" else "")
                t.setBackgroundColor(if (item.path == p.selected) Color.parseColor("#BBDEFB") else Color.TRANSPARENT)
                return t
            }
        }
    }

    private fun up(p: Pane) {
        p.selected = null
        if (p.archive != null) {
            if (p.inner == "") p.archive = null
            else {
                val s = p.inner.trimEnd('/').substringBeforeLast('/', "")
                p.inner = if (s == "") "" else "$s/"
            }
        } else p.dir.parentFile?.let { p.dir = it }
    }

    private fun onTap(p: Pane, item: Item) {
        if (item.name == "..") { up(p); refreshAll(); return }
        p.selected = null
        if (p.archive != null) {
            if (item.isDir) p.inner = item.path else p.selected = item.path
        } else if (item.isDir) p.dir = File(item.path)
        else if (item.name.lowercase().let { it.endsWith(".apk") || it.endsWith(".zip") || it.endsWith(".jar") }) {
            p.archive = File(item.path); p.inner = ""
        } else p.selected = item.path
        refreshAll()
    }

    private fun onLong(p: Pane, item: Item) {
        if (item.name == "..") return
        val opts = ArrayList<String>()
        opts.add("Seç")
        if (p.archive == null && item.name.lowercase().endsWith(".apk")) opts.add("Bu APK'yı imzala")
        AlertDialog.Builder(this).setTitle(item.name).setItems(opts.toTypedArray()) { _, w ->
            if (opts[w] == "Seç") { p.selected = item.path; refreshAll() }
            else signApk(File(item.path))
        }.show()
    }

    private fun copy() {
        val s = panes[active]; val t = panes[1 - active]
        val item = s.items.find { it.path == s.selected }
        if (item == null) { toast("Önce bir öğe seç (dosyaya dokun / uzun bas)"); return }
        Thread {
            try {
                val a = s.archive; val b = t.archive
                when {
                    a == null && b == null -> File(item.path).copyRecursively(File(t.dir, item.name), true)
                    a == null && b != null -> ZipEditor.add(b, t.inner, File(item.path))
                    a != null && b == null -> ZipEditor.extract(a, item.path, s.inner, t.dir)
                    else -> throw Exception("Arşivden arşive kopyalama desteklenmiyor")
                }
                ui { toast(if (b != null) "Eklendi. APK değişti, tekrar imzalamayı unutma." else "Tamam"); refreshAll() }
            } catch (e: Exception) { ui { toast("Hata: ${e.message}") } }
        }.start()
    }

    private fun delete() {
        val s = panes[active]
        val item = s.items.find { it.path == s.selected }
        if (item == null) { toast("Önce bir öğe seç"); return }
        AlertDialog.Builder(this).setMessage("${item.name} silinsin mi?")
            .setPositiveButton("Sil") { _, _ ->
                Thread {
                    try {
                        val a = s.archive
                        if (a != null) ZipEditor.remove(a, item.path) else File(item.path).deleteRecursively()
                        s.selected = null
                        ui { refreshAll() }
                    } catch (e: Exception) { ui { toast("Hata: ${e.message}") } }
                }.start()
            }.setNegativeButton("İptal", null).show()
    }

    private fun keyDialog() {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 10, 30, 0) }
        fun field(h: String, v: String): EditText {
            val e = EditText(this); e.hint = h; e.setText(v); l.addView(e); return e
        }
        val path = field("Anahtar yolu (.p12 / .bks)", prefs.getString("path", Environment.getExternalStorageDirectory().absolutePath + "/Download/erensari.p12")!!)
        val alias = field("Alias", prefs.getString("alias", "erensari")!!)
        val pass = field("Şifre", prefs.getString("pass", "")!!)
        AlertDialog.Builder(this).setTitle("İmza anahtarı").setView(l)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.edit().putString("path", path.text.toString().trim())
                    .putString("alias", alias.text.toString().trim())
                    .putString("pass", pass.text.toString()).apply()
                toast("Kaydedildi")
            }.show()
    }

    private fun signApk(apk: File) {
        val ks = File(prefs.getString("path", "") ?: "")
        if (!ks.exists()) { toast("Önce 'Anahtar' düğmesinden anahtar dosyasını ayarla"); return }
        val out = File(apk.parentFile, apk.nameWithoutExtension + "_signed.apk")
        toast("İmzalanıyor...")
        Thread {
            try {
                ZipEditor.sign(apk, out, ks, prefs.getString("alias", "")!!, prefs.getString("pass", "")!!)
                ui { toast("İmzalandı: ${out.name}"); refreshAll() }
            } catch (e: Exception) { ui { toast("İmza hatası: ${e.message}") } }
        }.start()
    }
}
