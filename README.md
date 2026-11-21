# Passador Replay

Aplicativo Android para copiar arquivos de replay entre duas pastas escolhidas pelo usuário.

## Build no GitHub

1. Crie um repositório vazio no GitHub.
2. Copie o conteúdo desta pasta para a raiz do repositório.
3. Faça push para `main`.
4. Abra a aba **Actions**.
5. Baixe o artefato `passador-replay-debug` ao final do workflow.

## Build local

Com JDK 17 e Gradle 8.9 instalados:

```powershell
gradle --no-daemon assembleDebug
```

APK gerado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Uso

1. Toque em **Começar**.
2. Escolha a pasta de origem.
3. Escolha a pasta de destino.
4. Selecione a direção desejada.
5. Toque em **Copiar replays**.

O app usa o Storage Access Framework, não acessa pastas sem consentimento do usuário e não executa comandos ADB, bypass ou desinstalação automática.
