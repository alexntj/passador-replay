package com.passador.replay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var screenConnect: android.view.View
    private lateinit var screenDirection: android.view.View
    private lateinit var screenTransfer: android.view.View
    private lateinit var logBox: TextView
    private lateinit var btnPassar: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvDestination: TextView
    private var directionMaxToNormal = true
    private var sourceUri: Uri? = null
    private var destinationUri: Uri? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val pickSource = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        sourceUri = uri
        persistPermission(uri)
        tvSource.text = "Origem: ${uri.lastPathSegment ?: "pasta selecionada"}"
        refreshTransferState()
        log("Pasta de origem selecionada")
    }

    private val pickDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        destinationUri = uri
        persistPermission(uri)
        tvDestination.text = "Destino: ${uri.lastPathSegment ?: "pasta selecionada"}"
        refreshTransferState()
        log("Pasta de destino selecionada")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        screenConnect = findViewById(R.id.screenConnect)
        screenDirection = findViewById(R.id.screenDirection)
        screenTransfer = findViewById(R.id.screenTransfer)
        logBox = findViewById(R.id.logBox)
        btnPassar = findViewById(R.id.btnPassar)
        tvStatus = findViewById(R.id.tvStatus)
        tvSource = findViewById(R.id.tvSource)
        tvDestination = findViewById(R.id.tvDestination)

        findViewById<Button>(R.id.btnConectar).setOnClickListener { openDeveloperOptions() }
        findViewById<Button>(R.id.btnJaConectei).setOnClickListener {
            fadeOut(screenConnect)
            fadeIn(screenDirection)
        }
        findViewById<Button>(R.id.btnMaxToNormal).setOnClickListener {
            directionMaxToNormal = true
            showTransferScreen("FF MAX  →  FF NORMAL")
        }
        findViewById<Button>(R.id.btnNormalToMax).setOnClickListener {
            directionMaxToNormal = false
            showTransferScreen("FF NORMAL  →  FF MAX")
        }
        findViewById<Button>(R.id.btnSource).setOnClickListener { pickSource.launch(null) }
        findViewById<Button>(R.id.btnDestination).setOnClickListener { pickDestination.launch(null) }
        btnPassar.setOnClickListener { copyReplays() }
        screenConnect.alpha = 0f
        screenConnect.animate().alpha(1f).setDuration(500).start()
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            log("Opções de desenvolvedor abertas")
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showTransferScreen(title: String) {
        findViewById<TextView>(R.id.tvTransferTitle).text = title
        tvStatus.text = "Escolha as pastas de origem e destino"
        tvStatus.setTextColor(0xFFFF1A1A.toInt())
        fadeOut(screenDirection)
        fadeIn(screenTransfer)
        refreshTransferState()
    }

    private fun persistPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun refreshTransferState() {
        btnPassar.isEnabled = sourceUri != null && destinationUri != null
        btnPassar.alpha = if (btnPassar.isEnabled) 1f else 0.45f
        if (sourceUri != null && destinationUri != null) {
            tvStatus.text = "Tudo pronto para copiar"
            tvStatus.setTextColor(0xFFFF1A1A.toInt())
        }
    }

    private fun copyReplays() {
        val source = sourceUri ?: return
        val destination = destinationUri ?: return
        btnPassar.isEnabled = false
        log("Iniciando transferência...")
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
                    log("Nenhum replay encontrado na origem")
                    return@execute
                }
                var count = 0
                files.forEach { file ->
                    val name = file.name ?: "replay"
                    val target = destinationDir.findFile(name)
                        ?: destinationDir.createFile(file.type ?: "application/octet-stream", name)
                    if (target == null) {
                        log("Falha ao criar $name")
                        return@forEach
                    }
                    contentResolver.openInputStream(file.uri).use { input ->
                        contentResolver.openOutputStream(target.uri, "wt").use { output ->
                            requireNotNull(input) { "entrada indisponível" }
                            requireNotNull(output) { "saída indisponível" }
                            input.copyTo(output)
                        }
                    }
                    count++
                    log("✓ $name")
                }
                log("Concluído. $count arquivo(s) copiado(s).")
            } catch (error: Exception) {
                log("Erro: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                runOnUiThread { refreshTransferState() }
            }
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logBox.append("[$time] $message\n")
            findViewById<ScrollView>(R.id.logScroll).post {
                findViewById<ScrollView>(R.id.logScroll).fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun fadeOut(view: android.view.View) {
        view.animate().alpha(0f).setDuration(260).withEndAction { view.visibility = android.view.View.GONE }.start()
    }

    private fun fadeIn(view: android.view.View) {
        view.alpha = 0f
        view.visibility = android.view.View.VISIBLE
        view.animate().alpha(1f).setDuration(300).start()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
