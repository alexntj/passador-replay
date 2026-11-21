package com.passador.replay

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var tvSource: TextView
    private lateinit var tvDestination: TextView
    private lateinit var logBox: TextView
    private lateinit var btnTransfer: Button
    private var sourceUri: Uri? = null
    private var destinationUri: Uri? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val pickSource = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        sourceUri = uri
        persistPermission(uri)
        tvSource.text = "Origem: ${uri.lastPathSegment ?: "pasta selecionada"}"
        refreshButton()
        log("Pasta de origem selecionada")
    }

    private val pickDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        destinationUri = uri
        persistPermission(uri)
        tvDestination.text = "Destino: ${uri.lastPathSegment ?: "pasta selecionada"}"
        refreshButton()
        log("Pasta de destino selecionada")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvSource = findViewById(R.id.tvSource)
        tvDestination = findViewById(R.id.tvDestination)
        logBox = findViewById(R.id.logBox)
        btnTransfer = findViewById(R.id.btnTransfer)
        findViewById<Button>(R.id.btnSource).setOnClickListener { pickSource.launch(null) }
        findViewById<Button>(R.id.btnDestination).setOnClickListener { pickDestination.launch(null) }
        btnTransfer.setOnClickListener { copyReplays() }
        log("Pronto. Selecione as pastas de origem e destino.")
    }

    private fun persistPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun refreshButton() {
        btnTransfer.isEnabled = sourceUri != null && destinationUri != null
    }

    private fun copyReplays() {
        val source = sourceUri ?: return
        val destination = destinationUri ?: return
        btnTransfer.isEnabled = false
        log("Iniciando cópia...")
        executor.execute {
            try {
                val sourceDir = DocumentFile.fromTreeUri(this, source)
                val destinationDir = DocumentFile.fromTreeUri(this, destination)
                if (sourceDir == null || destinationDir == null) {
                    log("Não foi possível abrir uma das pastas")
                    return@execute
                }
                val files = sourceDir.listFiles().filter { it.isFile }
                if (files.isEmpty()) {
                    log("Nenhum arquivo encontrado na origem")
                    return@execute
                }
                var copied = 0
                files.forEach { file ->
                    val target = destinationDir.findFile(file.name ?: "")
                        ?: destinationDir.createFile(file.type ?: "application/octet-stream", file.name ?: "replay")
                    if (target == null) {
                        log("Falha ao criar ${file.name}")
                        return@forEach
                    }
                    contentResolver.openInputStream(file.uri).use { input ->
                        contentResolver.openOutputStream(target.uri, "wt").use { output ->
                            if (input == null || output == null) throw IllegalStateException("stream indisponível")
                            input.copyTo(output)
                        }
                    }
                    copied++
                    log("✓ ${file.name}")
                }
                log("Concluído: $copied arquivo(s) copiado(s).")
            } catch (error: Exception) {
                log("Erro: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                runOnUiThread { refreshButton() }
            }
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logBox.append("[$time] $message\n")
            findViewById<ScrollView>(R.id.logScroll).post { findViewById<ScrollView>(R.id.logScroll).fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
