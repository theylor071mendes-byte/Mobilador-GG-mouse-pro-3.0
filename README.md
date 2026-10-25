# MOBILADOR PRO X

App Android (Kotlin + Jetpack Compose) para jogar com teclado, mouse e
controles conectados por USB-OTG ou Bluetooth — com perfis separados por
jogo (Free Fire, outros jogos e perfis personalizados), ajustes de
sensibilidade/"DPI" e um painel flutuante arrastável para calibrar a
posição de cada botão na tela.

## O que o app realmente faz (e como)

Tudo aqui usa **APIs públicas e documentadas do Android**. Não há root,
injeção de código em outros apps, leitura de memória de jogos nem
contorno de sistema anticheat.

- **Mapeamento de teclado → toques na tela**: um [Serviço de
  Acessibilidade](app/src/main/java/com/mobiladorprox/app/service/MapperAccessibilityService.kt)
  lê as teclas do teclado físico e usa `dispatchGesture` (API oficial de
  gestos de acessibilidade) para simular toque único ou pressionar-e-segurar
  nas coordenadas calibradas pelo usuário.
- **"Suspender o mouse" / controle de câmera**: usa a [API de Captura de
  Ponteiro](app/src/main/java/com/mobiladorprox/app/service/PointerCaptureView.kt)
  (`requestPointerCapture`, disponível desde o Android 8.0), que prende o
  cursor e entrega o movimento relativo do mouse. Esse movimento é
  convertido em pequenos gestos de arrasto repetidos para mover a câmera —
  **é uma simulação por gestos, não um cursor nativo do jogo**, então a
  suavidade é diferente da de um mouse em PC.
- **Painel flutuante arrastável**: um [serviço em primeiro
  plano](app/src/main/java/com/mobiladorprox/app/service/OverlayService.kt)
  desenha uma janela de overlay (`TYPE_APPLICATION_OVERLAY`) com um botão
  que pode ser arrastado pela tela, e um "Modo edição" para posicionar cada
  botão do jogo exatamente onde você precisa.
- **Perfis e configurações**: salvos localmente em um banco [Room](app/src/main/java/com/mobiladorprox/app/data/db)
  (SQLite), sobrevivem ao fechar o app. Suporta criar, editar, duplicar,
  renomear, excluir e restaurar os padrões de cada perfil.
- **Otimização**: a tela [Otimização](app/src/main/java/com/mobiladorprox/app/ui/optimization/OptimizationScreen.kt)
  mostra RAM disponível, modelo do aparelho, versão do Android, taxa de
  atualização da tela (quando o sistema informa) e estado de USB/Bluetooth —
  tudo lido na hora, sem estimativas. **Não há nenhuma promessa de aumento
  de FPS** em lugar nenhum do app.

## Limitações importantes (leia antes de usar)

- Os toques são simulados por gestos; a precisão depende de você calibrar
  a posição de cada botão para a resolução do seu aparelho e o layout do
  jogo.
- Enquanto a câmera por mouse está ativa, a janela de captura assume o
  foco da tela. **Alguns jogos (inclusive o Free Fire) podem pausar ou
  mostrar o menu de pausa ao perceber perda de foco de janela** — isso é
  comportamento do jogo/Android, não um defeito do app.
- Muitos jogos competitivos, incluindo o Free Fire, tratam o uso de
  teclado/mouse mapeado como vantagem não permitida nos seus termos de
  uso e podem penalizar a conta. Essa decisão é das desenvolvedoras do
  jogo — use por sua conta e risco.
- minSdk 26 (Android 8.0) — a API de Captura de Ponteiro exige essa
  versão como mínimo.

## Como instalar

1. Baixe o `.apk` mais recente na aba **Releases** deste repositório
   (tag `build-N`, a de número mais alto).
2. Transfira para o celular e instale (autorize "instalar de fontes
   desconhecidas" se solicitado).
3. Abra o app, toque em **INICIAR MAPEADOR** e siga o fluxo: ativar o
   Serviço de Acessibilidade → permitir sobreposição → escolher um
   perfil.
4. Conecte seu teclado/mouse/controle (Bluetooth ou USB-OTG) e jogue.
   Use o botão flutuante para abrir o mini-painel (Mapeador / Câmera /
   Modo edição).

## Como editar este projeto pelo celular

Você não precisa de computador para mexer no código:

1. No navegador do celular, abra este repositório no GitHub.
2. Para editar um arquivo existente: abra o arquivo → ícone de lápis
   (editar) → faça a alteração → **Commit changes** direto na branch
   `main`.
3. Para uma edição maior com múltiplos arquivos, toque no `.` do teclado
   do celular não ajuda — em vez disso, troque a URL de
   `github.com/...` para `github.dev/...` (mesmo endereço, trocando só o
   domínio) para abrir um editor completo (estilo VS Code) direto no
   navegador, inclusive pelo celular.
4. Qualquer commit enviado para a branch `main` dispara automaticamente
   a compilação (veja abaixo) e gera um novo APK em **Releases** em
   poucos minutos.

## Como a compilação automática funciona

O fluxo em [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)
roda no GitHub Actions a cada push na branch `main`:

1. Prepara Java 17 e aceita as licenças do Android SDK já instalado no
   runner do GitHub.
2. Compila com `gradle assembleDebug`.
3. Publica o log completo da compilação como uma Release
   (`logs-N`) — sucesso ou falha —, o que facilita encontrar o motivo de
   qualquer erro.
4. Se a compilação for bem-sucedida, publica o `.apk` gerado como uma
   Release (`build-N`), pronto para baixar direto da aba Releases.

### Gerar o APK de Release (assinado), opcionalmente

O workflow atual gera o **APK de depuração** (`debug`), que já pode ser
instalado normalmente. Para gerar um APK de **Release** assinado de
verdade (necessário para publicar em lojas de aplicativos, por exemplo),
seria preciso:

1. Criar um keystore de assinatura (`keytool -genkeypair ...`).
2. Guardar o keystore e as senhas como *Secrets* deste repositório
   (Settings → Secrets and variables → Actions).
3. Adicionar um bloco `signingConfigs` em `app/build.gradle` lendo esses
   secrets, e um job extra `assembleRelease` no workflow.

Isso não foi configurado aqui porque exigiria gerar e guardar uma chave
de assinatura seguindo decisões só suas (nome, validade, senha) — é um
passo deliberadamente deixado para quando você precisar dele.

## Como compilar você mesmo no Android Studio (opcional)

1. Abra esta pasta no Android Studio (Hedgehog ou mais novo).
2. Deixe o Android Studio baixar Gradle/SDK automaticamente.
3. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.

## Estrutura do projeto

```
app/src/main/java/com/mobiladorprox/app/
  data/            Room (perfis e vínculos de tecla), repositório, perfis padrão
  service/         Serviço de Acessibilidade, overlay flutuante, captura de ponteiro
  settings/        Preferências persistentes (DataStore)
  ui/              Telas em Jetpack Compose (Início, Perfis, Editor, Otimização, Permissões)
  MainActivity.kt  Navegação entre telas
  MobiladorApplication.kt  Monta as dependências do app
```
