# Ghost Mine

App Android (Kotlin + Jetpack Compose) de mineração real de Bitcoin na CPU (SHA-256d via Stratum V1).

## Gerar o APK no GitHub
1. Crie um repositório e envie esta pasta (branch `main`).
2. Aba **Actions → Build APK** (roda sozinho no push, ou clique em *Run workflow*).
3. Baixe o artefato **ghost-mine-apk** (`app-debug.apk` e `app-release.apk`).
4. No celular, permita instalar apps de fontes desconhecidas e instale o APK.

## Gerar localmente
Requer JDK 17 e Android SDK 34. Com Gradle 8.9 instalado:

    gradle wrapper --gradle-version 8.9   # uma vez, gera o ./gradlew
    ./gradlew assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew assembleRelease             # assinado com a chave debug (troque para distribuir)
    ./gradlew :app:testDebugUnitTest      # testes de validação de endereço

## Estrutura
- `btc/` validação de endereço (Base58Check, Bech32, Bech32m): Kotlin puro
- `stratum/` cliente Stratum V1 (TCP/TLS): Kotlin/JVM puro
- `miner/MinerEngine` engine SHA-256d: Kotlin/JVM puro. `MinerController` liga engine, pool, proteção térmica e sessões
- `hardware/` detecção de aparelho, bateria, temperatura e perfil recomendado
- `data/Repo` configurações (EncryptedSharedPreferences), histórico e amostras
- `notify/`, `service/` notificações e foreground service
- `ui/` Compose: Home, Mining, Statistics, Wallet, Settings, Pool, History

`btc`, `stratum` e `miner/MinerEngine` não usam classes Android, então podem ir para um módulo compartilhado em uma versão desktop.

## Limites honestos
- Um celular minera em kH/s~MH/s; a rede Bitcoin está em centenas de EH/s. O ganho esperado é praticamente zero.
- Google Play proíbe mineração no aparelho: distribuição apenas por APK.
- Todos os números (hashrate, shares, ping) vêm da engine e do pool. "Est. BTC" é calculado a partir dos hashes reais e da dificuldade da rede (3,125 BTC/bloco, sem taxas).
- Saldo pendente, total recebido e histórico de pagamentos dependem de API do pool (não existe no Stratum puro): a tela mostra "Not reported by pool" em vez de inventar valores. A notificação "Payment received" está implementada, mas só dispara quando houver integração com a API de um pool.
- Preço em USD não foi integrado (sem fonte de preço definida).
- Engine em Kotlin/JVM (MessageDigest). Um motor em C/NDK com instruções SHA ARM seria mais rápido e é a próxima otimização.
