# Build e atualizações do Kosen

O **Kosen** é um leitor de mangá independente. Este guia explica como compilar localmente e publicar atualizações para os usuários.

## Pré-requisitos

- JDK 17
- Android SDK (API 36)
- Git
- Clone do repositório **privado** `kosen-parsers` (obrigatório — JitPack não funciona com repo privado):

```powershell
# Estrutura recomendada (monorepo local):
# kaisoku/
#   Kaisoku/          ← este app
#   kosen-parsers/    ← clone aqui

git clone git@github.com:Ithan-Cassiano/kosen-parsers.git ..\kosen-parsers
```

Ou clone dentro da pasta do app: `git clone ... Kaisoku\kosen-parsers`

## Build de desenvolvimento (debug)

Instala junto com a versão release — usa o pacote `com.kosen.reader.dev` (legado: `com.kosen.reader.debug`).

**Opção 1 — a partir da raiz do projeto (`kaisoku/`):**

```powershell
cd C:\Users\ithan\Downloads\kaisoku
.\build.ps1 -Variant debug
```

**Opção 2 — dentro da pasta do app:**

```powershell
cd C:\Users\ithan\Downloads\kaisoku\Kosen
.\gradlew.bat assembleDebug
```

> O `gradlew.bat` fica dentro de `Kosen/`, não na pasta pai.

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

# Ou dentro de Kosen/:
cd Kosen
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

| App | Repositório | Visibilidade |
|-----|-------------|--------------|
| **Kosen** (release) | `Ithan-Cassiano/Kosen-Releases` | **Privado** (app pago) |
| **Kosen Dev** | `Ithan-Cassiano/Kosen-Dev-Releases` | **Privado** |

O código-fonte fica em repos **privados** (`Kosen`/`Kaisoku`, `kosen-parsers`).

### Repositórios privados — checklist

| Repo | Visibilidade | Observação |
|------|--------------|------------|
| `Ithan-Cassiano/Kosen` ou `Kaisoku` | **Private** | Código do app |
| `Ithan-Cassiano/kosen-parsers` | **Private** | Fontes de mangá |
| `Ithan-Cassiano/Kosen-Dev-Releases` | **Private** | OTA do app dev |
| `Ithan-Cassiano/Kosen-Releases` | **Private** | OTA release (APKs pagos) |

Reportes de bug via **e-mail** (`mailto:` em `constants.xml`). Links públicos do GitHub foram removidos do app.

### Kosen Release — canal privado (app pago)

1. Torne `Kosen-Releases` **Private** no GitHub.
2. Crie um **fine-grained PAT** só para OTA (leitura):
   - Nome sugerido: `Kosen Release OTA (read-only)`
   - Repositório: **apenas** `Kosen-Releases`
   - Permissão: **Contents: Read-only**
3. Adicione em `local.properties` **antes** de compilar o release:

```properties
kosen.release_github_token=github_pat_...
```

Alternativa: variável de ambiente `KOSEN_RELEASE_GITHUB_TOKEN` ao rodar `assembleRelease`.

4. Recompile: `.\build.ps1 -Variant release` — o token entra no APK e permite checar/baixar updates do repo privado.

> **Atenção:** o token fica embutido no APK (read-only). Quem extrair o APK pode baixar releases, mas **não** altera o repo. Rotacione o PAT se vazar.
>
> **APKs antigos** (compilados sem `kosen.release_github_token`) **param de receber OTA** quando o repo ficar privado. Publique uma versão nova após configurar o token.

5. **Publicar** releases continua com outro token (escrita) — veja abaixo. **Não** use o token de escrita no APK.

#### Token para publicar releases (só na sua máquina / CI)

Crie um PAT **separado** com **Contents: Read and write** em `Kosen-Releases`:

- Usado por `Get-GitHubToken.ps1` / `publish-public-release.ps1`
- **Nunca** coloque em `kosen.release_github_token` (não vai para o APK)
- Pode ser classic PAT ou fine-grained com escopo de escrita só nesse repo

Resumo de tokens:

| Token | Onde | Permissão | Vai para o APK? |
|-------|------|-----------|-----------------|
| `kosen.release_github_token` | `local.properties` | Read em `Kosen-Releases` | **Sim** (release) |
| `kosen.dev_github_token` | `local.properties` | Read em `Kosen-Dev-Releases` | **Sim** (debug) |
| `Get-GitHubToken.ps1` / env | sua sessão | Write em `Kosen-Releases` | **Não** |
| `KOSEN_REPO_PAT` | GitHub Actions secret | Read em `kosen-parsers` | **Não** |

### CI (GitHub Actions) com repos privados

O `GITHUB_TOKEN` padrão **não acessa** outro repositório privado. Crie um **fine-grained PAT** com **Contents: Read-only** nos repos `Kosen`/`Kaisoku` e `kosen-parsers`, e adicione como secret:

| Secret | Descrição |
|--------|-----------|
| `KOSEN_REPO_PAT` | PAT read-only para checkout de `kosen-parsers` nos workflows |

Sem esse secret, o job falha ao clonar `kosen-parsers`.

### Kosen Dev — canal privado

1. O repo `Kosen-Dev-Releases` deve estar **Private** no GitHub (Settings → General → Danger zone).
2. Antes de compilar o debug, adicione em `local.properties`:

```properties
kosen.dev_github_token=ghp_...ou_github_pat_...
```

Use um **fine-grained token** com permissão **Contents: Read-only** no repo `Kosen-Dev-Releases`.

Opcional: o mesmo token pode ser usado como variável de ambiente `GH_TOKEN` ao compilar debug localmente (alternativa a `local.properties`).

3. Recompile: `.\gradlew.bat assembleDebug` — o token entra no APK dev e permite checar/baixar updates do repo privado.

> Também é possível definir o token **depois da instalação** em Configurações → Debug → Token GitHub (OTA dev).

> **Não compartilhe** o APK dev publicamente — ele contém credencial de leitura do repo privado.
> Distribua só para testadores autorizados (instalação manual ou update in-app).

### Como publicar uma atualização dev

```powershell
cd scripts
.\publish-dev-release.ps1 -Version 1.0.17
```

### Como publicar uma atualização release

**Só execute quando o usuário pedir** (build, release, commit, etc.).

1. Atualize `versionCode` e `versionName` em `app/build.gradle`
2. Escreva a **descrição** da release (aparece no app ao atualizar)
3. **Release privada** (repo `Kosen`):

```powershell
cd scripts
.\publish-release.ps1 -Version 9.7.14 -Description "## O que há de novo`n- ...`n`n## Correções`n- ..."
```

4. **Release para clientes** (repo privado `Kosen-Releases`):

```powershell
.\publish-public-release.ps1 -Version 9.7.14 -Description "..."
```

(Requer token de **escrita** no GitHub — não confundir com `kosen.release_github_token`.)

5. **Build** — somente se pedir build ou não houver APK:

```powershell
.\build.ps1 -Variant release
```

> `-Description` é **obrigatório** em toda publicação.
> Usuários só veem update se existir release com APK em `Kosen-Releases`.

### Secrets necessários no GitHub (para CI)

| Secret | Descrição |
|--------|-----------|
| `KOSEN_REPO_PAT` | PAT read-only para checkout de `kosen-parsers` nos workflows |
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

Em desenvolvimento, os parsers locais em `../kosen-parsers` ou `kosen-parsers/` são usados automaticamente via `includeBuild`.

**Não há fallback JitPack** — o repositório `kosen-parsers` é privado. O Gradle falha com instruções de clone se a pasta não existir.

No CI, o workflow faz checkout de `Ithan-Cassiano/kosen-parsers` usando o secret `KOSEN_REPO_PAT`.

## Identidade do app

| Item | Valor |
|------|-------|
| Nome | Kosen |
| applicationId (release) | `com.kosen.reader` |
| applicationId (debug) | `com.kosen.reader.dev` (legado: `.debug`) |
| Deep link | `kosen://` |

## Script rápido (Windows)

```powershell
.\scripts\build.ps1 -Variant debug
.\scripts\build.ps1 -Variant release
```

## Problemas comuns

### "O aplicativo não foi instalado" (Samsung / Android)

No Galaxy (S24 etc.), essa mensagem genérica costuma ser uma destas causas:

| Causa | O que fazer |
|--------|-------------|
| **Assinatura diferente** (atualização) | Desinstale **Kosen Dev** (`com.kosen.reader.debug`) ou **Kosen** (`com.kosen.reader`) e instale o APK de novo |
| **APK release sem assinatura** | Release sem `kosen.storeFile` em `local.properties` gera `app-release-unsigned.apk` → **não instala**. Use `assembleDebug` ou configure o keystore release |
| **Versão mais antiga** (downgrade) | Só instale um APK com `versionCode` maior que o já instalado |
| **Download incompleto** | Baixe o APK de novo (GitHub ou cópia local); confira ~30 MB (dev) ou ~16 MB (release) |
| **Play Protect** | Configurações → Segurança → Instalar apps desconhecidos: permitir o app usado (Chrome, Arquivos, etc.) |

**Dois apps diferentes:**

- **Kosen Dev** — pacote `com.kosen.reader.dev` (legado: `.debug`) — repo **privado**
- **Kosen** — pacote `com.kosen.reader` (release pública)

Podem ficar instalados ao mesmo tempo. Não misture APK dev em cima de release (são pacotes diferentes).

**Após v1.0.10 (dev):** os APKs dev usam keystore compartilhado do projeto (`keystore/kosen-dev.keystore`), para atualizações OTA e manuais usarem a **mesma assinatura**. Se você tinha uma versão dev antiga instalada, **desinstale uma vez** e instale de novo.

Depois de configurar o keystore release, o APK correto será `app-release.apk` (sem `-unsigned` no nome).
