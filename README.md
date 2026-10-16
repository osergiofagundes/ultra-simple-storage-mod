# Ultra Simple Storage — mod para Minecraft Java 26.3 (Fabric)

```
Mesa Hub > Conector > Cabo > Cabo > Cabo > Conector > Baú
```

A **Mesa Hub** mostra, numa tela só, todos os itens de todos os baús ligados a ela por **cabos** e **conectores**. Dá para pesquisar, tirar e guardar itens sem abrir baú nenhum.

## O que já funciona

| Bloco | Para que serve |
|---|---|
| **Mesa Hub** (`storage_hub`) | Botão direito abre a tela da rede. A face da tela fica virada para fora de onde você colocou: chão, parede ou teto |
| **Cabo de Rede** (`network_cable`) | Liga as peças; conecta sozinho nos 6 lados |
| **Conector de Baú** (`chest_connector`) | Encosta no baú. **Coloque clicando na face do baú** — a "boca" verde fica virada para ele. Nos outros 5 lados ele se liga sozinho aos cabos |

Na tela da Mesa Hub:

- **Barra de pesquisa** no topo: clique nela e digite. Não diferencia maiúsculas nem acentos (`tabua` acha "Tábua"). Botão direito nela limpa o texto. Clicando fora dela, o "E" volta a fechar a tela.
- Cada item mostra o **total real** no canto (`812`, `3,4K`, `1,2M`); passando o mouse aparece o número exato (`Total: 3.456`).
- **Ordenação** pelos 3 botões ao lado da busca: `A-Z` (nome), `↓` (maior quantidade primeiro, o padrão) e `↑` (menor primeiro). A escolha fica lembrada enquanto o jogo estiver aberto.
- **Roda do mouse** ou **arrastar a barra** da direita → rola a lista.
- **Clique esquerdo** num item → pega 1 pilha. **Direito** → meia pilha.
- **Shift+clique** num item da rede → vai direto para o seu inventário.
- **Shift+clique** num item do seu inventário → é guardado na rede (primeiro nos baús que já têm esse item).
- **Soltar um item** (que está no cursor) sobre a grade → guarda. Botão direito guarda só 1.

Funciona com baú, baú duplo (sem contar em dobro), barril, shulker box e baús de outros mods que usem a Transfer API do Fabric.

### Receitas

- Mesa Hub: `pedra lisa, ferro, pedra lisa / redstone, baú, redstone / pedra lisa, ferro, pedra lisa`
- Conector (x4): `cabo, cabo, cabo / cabo, redstone, cabo / cabo, cabo, cabo`
- Cabo (x16): `ferro, redstone, ferro / pedra lisa, pedra lisa, pedra lisa`

As receitas aparecem no livro de receitas assim que você pega redstone (cabo e Mesa) ou um cabo (conector).

## Como rodar

1. Instale o **JDK 25** (ex.: Eclipse Temurin 25). O Minecraft 26.x exige Java 25.
2. Instale o **IntelliJ IDEA Community** e abra esta pasta (*File → Open*). Ele importa o Gradle sozinho; a primeira vez demora porque baixa o Minecraft, o Fabric Loader e a Fabric API.
3. Para testar: na aba Gradle procure a tarefa `runClient` (ou use o terminal):
   - Windows: `gradlew.bat runClient`
   - Linux/Mac: `./gradlew runClient`
4. Para gerar o `.jar`: `gradlew build` → arquivo em `build/libs/chestnet-1.0.0.jar`. Coloque na pasta `mods` de uma instalação com **Fabric Loader 0.19.5+** junto com a **Fabric API para 26.3** (ela é obrigatória).
5. Teste automático: `gradlew runClientGameTest`. Abre o jogo sozinho por cerca de 1 minuto, cria um mundo e confere as receitas (na mesa de trabalho e no livro), a busca, a ordenação e o ESC da Mesa Hub. As screenshots ficam em `build/run/clientGameTest/screenshots`. O código está em `src/gametest`.

> **Atenção (Minecraft 26.x):** os números dos botões do mouse e das teclas mudaram. Agora o esquerdo é `1`, o direito é `3` e o ESC é `41`. Use sempre as constantes `InputConstants.MOUSE_BUTTON_LEFT`, `MOUSE_BUTTON_RIGHT` e `KEY_*`, nunca os números do GLFW.

## Como o código está organizado

```
src/main/java/com/sergiofagundes/chestnet/      (código comum: servidor + cliente)
  ChestNet.java              classe principal (ModInitializer)
  ModRegistry.java           registra blocos, itens, menu e aba criativa
  block/HubBlock.java        Mesa Hub: abre o menu
  block/CableBlock.java      cabo com conexões visuais nos 6 lados
  block/ConnectorBlock.java  conector com FACING apontando para o baú
  network/StorageNetwork.java  percorre a rede e lê/escreve nos baús
  menu/HubMenu.java          lógica da tela no servidor (cliques, envio da lista de itens)
  packet/HubContentsPayload  servidor → cliente: lista de itens da rede com os totais
  packet/HubActionPayload    cliente → servidor: clique na grade (pegar/guardar)
  packet/ModPackets.java     registra os pacotes e trata os cliques no servidor
src/client/java/com/sergiofagundes/chestnet/    (só cliente)
  client/HubScreen.java      desenho da tela: busca, grade, totais e rolagem
  client/ChestNetClient.java registra a tela e o recebimento da lista (ClientModInitializer)
src/main/resources/
  fabric.mod.json            ID, nome, entrypoints e dependências do mod
  assets/chestnet/...        modelos, texturas, traduções (pt_br e en_us)
  data/chestnet/...          receitas e drops
tools/GenTextures.java       gera as texturas (java tools/GenTextures.java)
```

Como a rede é encontrada: ao abrir a Mesa (e a cada 1 segundo com a tela aberta), `StorageNetwork.scan` faz uma busca em largura a partir da Mesa, andando por cabos, conectores e mesas vizinhos. Para cada conector, pega o inventário do bloco para onde ele aponta usando `ItemStorage.SIDED` da Transfer API do Fabric. Toda retirada/inserção usa `Transaction`, então nenhuma operação deixa item pela metade.

Como a tela funciona: a grade não usa slots. O servidor manda a lista completa (item + total) num `HubContentsPayload`, só quando ela muda. A busca e a rolagem acontecem só no cliente. Um clique na grade vira um `HubActionPayload`, e o servidor recalcula tudo antes de mexer nos baús, sem confiar nas quantidades do cliente.

## Ideias para as próximas versões

- **Filtros no conector**: escolher que itens podem entrar naquele baú.
- **Prioridade** entre baús (ex.: baú de minérios primeiro).
- **Cache da rede** no servidor em vez de reescanear a cada segundo (para redes muito grandes).
- Mesa com **grade de crafting** que puxa os ingredientes da rede.
