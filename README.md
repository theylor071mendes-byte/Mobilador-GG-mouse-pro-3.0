# KBM Mapper

App Android (Kotlin) que permite navegar pelo sistema usando um teclado
físico (USB-OTG ou Bluetooth) conectado ao celular.

## O que ele faz

- **Mouse**: cursor, clique e rolagem já funcionam nativamente no Android
  (9+) assim que você conecta um mouse USB ou Bluetooth — o app não
  precisa fazer nada para isso.
- **Teclado**: o app adiciona atalhos de navegação do sistema através de
  um Serviço de Acessibilidade (API pública do Android, sem root):
  - `Esc` → Voltar
  - Tecla `Windows`/`Meta` → Tela inicial
  - `Alt` + `Tab` → Apps recentes
  - `Ctrl` + `N` → Notificações
  - `F1` → Ajustes rápidos

Cada atalho pode ser ligado/desligado na tela principal do app.

## Privacidade

O app **não acessa a internet**, não lê o conteúdo da tela
(`canRetrieveWindowContent = false`) e não envia nenhum dado a lugar
nenhum. A permissão de Acessibilidade só é usada para simular os botões
de navegação do sistema.

## Como instalar

1. Baixe o `.apk` gerado pelo GitHub Actions (aba **Actions** → último
   build → artifact `kbm-mapper-debug-apk`).
2. Transfira o arquivo para o celular e instale (pode ser necessário
   permitir "instalar apps de fontes desconhecidas").
3. Abra o app e toque em **Ativar nos Ajustes de Acessibilidade** →
   encontre "KBM Mapper" na lista → ative.
4. Conecte seu teclado e mouse (Bluetooth ou USB-OTG) e use os atalhos.

## Como compilar você mesmo (Android Studio)

1. Abra esta pasta no Android Studio.
2. Deixe o Android Studio baixar o Gradle/SDK automaticamente (primeira
   vez que abrir o projeto).
3. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.

## Como o build automático funciona

O workflow em `.github/workflows/build-apk.yml` compila o app toda vez
que há um push na branch `main` (ou manualmente pela aba Actions), usando
o Android SDK e o Gradle instalados pelo próprio runner do GitHub — não
depende de nada local.
