# Build e atualizações do Kosen

O **Kosen** é um leitor de mangá independente. Este guia explica como compilar localmente e publicar atualizações para os usuários.

## Pré-requisitos

- JDK 17
- Android SDK (API 36)
- Git

## Build de desenvolvimento (debug)

Instala junto com a versão release — usa o pacote `com.kosen.reader.debug`.

**Opção 1 — a partir da raiz do projeto (`kaisoku/`):**

```powershell
cd C:\Users\ithan\Downloads\kaisoku
.\build.ps1 -Variant debug
```

**Opção 2 — dentro da pasta do app:**

```powershell
cd C:\Users\ithan\Downloads\kaisoku\Kaisoku
.\gradlew.bat assembleDebug
```

> O `gradlew.bat` fica dentro de `Kaisoku/`, não na pasta pai.

APK gerado em:

`app/build/outputs/apk/debug/app-debug.apk`

## Build de produção (release)

### 1. Criar keystore (apenas na primeira vez)

```powershell
keytool -genkey -v -keystore kosen-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kosen
```

### 2. Configurar assinatura

Copie `local.properties.example` para `local.properties` e preencha:

```properties
kosen.storeFile=../kosen-release.jks
kosen.storePassword=sua_senha
kosen.keyAlias=kosen
kosen.keyPassword=sua_senha
```

### 3. Compilar release

```powershell
# Na raiz do projeto:
.\build.ps1 -Variant release

# Ou dentro de Kaisoku/:
cd Kaisoku
.\gradlew.bat assembleRelease
```

APK gerado em:

`app/build/outputs/apk/release/app-release.apk`

## Build nightly (opcional)

```powershell
.\gradlew.bat assembleNightly
```

Pacote: `com.kosen.reader.nightly`

## Atualizações in-app

O app verifica automaticamente novas versões no repositório configurado em:

`app/src/main/res/values/constants.xml` → `github_updates_repo`

Padrão: `glitch-228/Kosen`

### Como publicar uma atualização

1. Atualize `versionCode` e `versionName` em `app/build.gradle`
2. Crie uma tag Git com prefixo `v`:

```powershell
git tag v9.7.3
git push origin v9.7.3
```

3. O workflow `.github/workflows/release.yml` compila o APK assinado e cria um GitHub Release
4. Usuários recebem a notificação de atualização dentro do app (Sobre → Verificar atualizações)

### Secrets necessários no GitHub (para CI)

| Secret | Descrição |
|--------|-----------|
| `KOSEN_KEYSTORE_BASE64` | Keystore `.jks` codificado em base64 |
| `KOSEN_STORE_PASSWORD` | Senha do keystore |
| `KOSEN_KEY_ALIAS` | Alias da chave |
| `KOSEN_KEY_PASSWORD` | Senha da chave |

Para gerar o base64 do keystore:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kosen-release.jks")) | Set-Clipboard
```

### Requisitos do APK no release

- O asset deve ser um `.apk`
- O `content_type` deve ser `application/vnd.android.package-archive` (padrão do GitHub)
- A tag deve seguir o padrão `vX.Y.Z` (ex: `v9.7.3`)

## Parsers (fontes)

Em desenvolvimento, os parsers locais em `../kaisoku-parsers` são usados automaticamente via `includeBuild`.

Para builds de CI/produção sem monorepo, publique `kosen-parsers` no JitPack e atualize o hash em `gradle/libs.versions.toml`.

## Identidade do app

| Item | Valor |
|------|-------|
| Nome | Kosen |
| applicationId (release) | `com.kosen.reader` |
| applicationId (debug) | `com.kosen.reader.debug` |
| Deep link | `kosen://` |

## Script rápido (Windows)

```powershell
.\scripts\build.ps1 -Variant debug
.\scripts\build.ps1 -Variant release
```

## Problemas comuns

### "O aplicativo não foi instalado" (Samsung / Android)

Quase sempre significa **APK sem assinatura válida**.

- Release **sem** `kosen.storeFile` em `local.properties` gera `app-release-unsigned.apk` → **não instala**
- Solução: configure o keystore (veja seção release acima) e recompile
- Para teste rápido no celular, use o APK **debug** (`assembleDebug`) — já vem assinado

Depois de configurar o keystore, o APK correto será `app-release.apk` (sem `-unsigned` no nome).
