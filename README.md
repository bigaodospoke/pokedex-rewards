# Pokedex Rewards

O comando `/poke` abre um menu com a porcentagem da Pokédex do jogador e libera
recompensas a cada faixa completada. A última, em 100%, entrega um Pokémon com
skin exclusiva.

- Minecraft 1.21.1 · Fabric · Cobblemon 1.8.x
- O menu é server-side: o jogador não precisa instalar nada

## Instalação

Joga `pokedex-rewards-1.0.0.jar` na pasta `mods/`, junto com o Cobblemon 1.8.x e
o Fabric API. O fabric-language-kotlin já vem embutido no Cobblemon.

Sobe o servidor uma vez — ele cria `config/pokedexrewards.json`.

## Comandos

| Comando | Permissão |
| --- | --- |
| `/poke` — abre o menu | todos |
| `/poke missoes` — missões diárias, semanais e mensais | todos |
| `/poke diario` — diário de capturas | todos |
| `/poke reload` — recarrega a config | op nível 2 |
| `/poke reset <jogador>` — apaga os resgates dele | op nível 2 |

`/pokerewards` e `/pokedexrewards` abrem o mesmo menu.

Mudar `command` ou `aliases` só vale depois de reiniciar o servidor. O resto o
`/poke reload` pega na hora.

## Config

Cada nível de recompensa:

```json
{
  "percent": 10,
  "title": "&a&l10%&r &7da Pokedex",
  "icon": "minecraft:iron_ingot",
  "rewards": ["&f15x &7Ultra Ball", "&f5x &7Rare Candy", "&f15.000 &7pokemoedas"],
  "commands": [
    "give %player% cobblemon:ultra_ball 15",
    "give %player% cobblemon:rare_candy 5",
    "eco give %player% 15000"
  ],
  "broadcast": null
}
```

- `rewards` é só o texto do menu; `commands` é o que acontece de verdade
- Placeholders: `%player%`, `%uuid%`, `%tier%`
- `broadcast` anuncia no chat do servidor; `null` não anuncia
- Cores com `&`, ou `&#FF00AA` pra hex
- `metric`: `CAUGHT` (só capturados) ou `SEEN` (vistos também)

### ⚠️ Ajustar o comando de dinheiro

As pokemoedas vêm como `eco give %player% 15000`, que é só um exemplo. Troque
pelo comando da economia do servidor, senão o jogador não recebe nada.

## A recompensa dos 100%

Hoje entrega um **Mewtwo shiny nível 100**, só de exemplo pra mostrar o formato.
A ideia é virar uma skin exclusiva depois — quando tiver, é só trocar o
`commands` desse nível na config.

## Missões diárias, semanais e mensais

Todas envolvem capturar. São **sorteadas pelo mod**, não escritas na mão: a cada
período ele sorteia 3 de cada tipo a partir dos modelos da config.

O sorteio usa o período como semente, então é o mesmo para todo mundo no
servidor e não muda se o servidor reiniciar no meio do dia.

Tipos de missão que o gerador pode sortear:

| Tipo | Missão |
| --- | --- |
| `CATCH_ANY` | capture N Pokémon |
| `CATCH_SHINY` | capture N shiny |
| `CATCH_TYPE` | capture N de um tipo sorteado |
| `CATCH_NEW_SPECIES` | capture N espécies que você nunca pegou |
| `CATCH_LEGENDARY` | capture N lendários |

Recompensas por período (diária básica → mensal a melhor), configuráveis em
`missions.daily`, `missions.weekly` e `missions.monthly`:

```json
"daily": {
  "missionCount": 3,
  "templates": [
    { "kind": "CATCH_ANY", "minTarget": 8, "maxTarget": 15 },
    { "kind": "CATCH_TYPE", "minTarget": 3, "maxTarget": 6 }
  ],
  "rewardsDisplay": ["&f10x &7Poke Ball"],
  "commands": ["give %player% cobblemon:poke_ball 10"]
}
```

Abre com `/poke missoes` ou pelo botão no menu principal. O reset usa o fuso de
`missions.timezone` (padrão `America/Sao_Paulo`).

## Voz da Pokédex

Ao selecionar um Pokémon na Pokédex do Cobblemon, uma voz narra a entrada —
nome, tipagem e a descrição da própria Pokédex:

> *"Venusaur. Pokémon do tipo Planta e Venenoso. Há uma grande flor nas costas
> do Venusaur. Dizem que a flor adquire cores vivas se receber bastante
> nutrição e luz solar..."*

São 1025 locuções, uma por espécie, embutidas no jar. Tudo sai dos dados do
próprio Cobblemon — inclusive os tipos e as descrições em português — então o
áudio bate exatamente com o texto na tela. Os 526 Pokémon de tipo duplo falam
as duas tipagens.

### ⚠️ Esta é a única parte que exige mod no cliente

O resto do mod é 100% servidor. A voz não dá: o clique na tela da Pokédex
**não gera pacote para o servidor** — a tela é do cliente e lê dados já
sincronizados. Dos pacotes serverbound de Pokédex do Cobblemon só existem
`StartScanningPacket` e `FinishScanningPacket`.

Então a voz usa um mixin em `PokedexGUI.setSelectedEntry`, que roda no cliente.
Na prática:

- quem **instalar o jar** ouve a voz e usa tudo o mais normalmente
- quem **não instalar** continua com missões, resgates e diário funcionando —
  só não ouve a voz

O mixin é declarado como `environment: client`, então o servidor nem carrega.

### Config da voz

Fica separada, em `config/pokedexrewards-voz.json`, na máquina do jogador — é
ele quem decide se quer ouvir:

```json
{
  "enabled": true,
  "volume": 1.0,
  "pitch": 1.0,
  "minIntervalMs": 250,
  "repeatCooldownMs": 1500
}
```

`minIntervalMs` evita falas sobrepostas quando se rola a lista rápido.

### Sobre a voz usada

É a voz `Microsoft Maria` (pt-BR), a versão moderna do Windows — acessada via
WinRT, não pelo SAPI antigo, que só expõe as vozes robóticas "Desktop". Gerada
uma vez e embutida como OGG. Não é clone de nenhum dublador.

Para trocar de voz depois (o Windows também tem `Microsoft Daniel`, masculina),
é só regerar os áudios: o texto de cada locução sai dos dados do Cobblemon.

### Por que não diz "o Pokémon Rato"

A categoria ("Mouse Pokémon") existe na PokéAPI em 11 idiomas, mas **não em
português** — e em espanhol são palavras inventadas (`Ratón`, `Acuartija`,
`Abazón`), não tradução literal. Traduzir as 643 categorias seria inventar
termos, com erro garantido em parte deles.

A descrição da Pokédex, por outro lado, **existe em português** no Cobblemon
(1025 delas), e é ela que a voz lê depois do nome e da tipagem.

## Diário de capturas

Estilo Pokémon GO: para cada espécie, onde e quando você pegou pela última vez,
e quantas vezes já pegou.

Abre de dois jeitos:

- **Shift + clique direito segurando qualquer Pokédex** do Cobblemon
- `/poke diario`, ou o botão no menu principal

O diário mostra bioma, dimensão, data/hora e — em rede — em qual servidor foi.
Ordenado da captura mais recente para a mais antiga, paginado.

> Isso **não** altera a tela da Pokédex do Cobblemon. Aquela tela é do cliente;
> mexer nela exigiria um mod no cliente de cada jogador. O diário é um
> inventário mandado pelo servidor, então continua funcionando sem ninguém
> instalar nada.

## Onde ficam os dados

Depende do `storage.mode` na config:

| Modo | Onde grava |
| --- | --- |
| `AUTO` (padrão) | segue o `storageFormat` do Cobblemon |
| `JSON` | `mundo/pokedexrewards/claims.json` |
| `MONGODB` | coleção `PokedexRewardsClaims` no mesmo banco do Cobblemon |

### Rede com vários servidores

Arquivo local **não serve** em rede: cada mundo tem o seu, então o jogador
resgata o mesmo prêmio em cada servidor. Duas coisas precisam estar
compartilhadas:

**1. A Pokédex** — é config do Cobblemon, não deste mod. Em
`config/cobblemon/main.json`, em todos os servidores:

```json
"storageFormat": "mongodb",
"mongoDBConnectionString": "mongodb://host:27017",
"mongoDBDatabaseName": "cobblemon"
```

**2. Os resgates** — com o `storage.mode` em `AUTO`, eles seguem o Cobblemon
sozinhos e vão pro mesmo banco. Não precisa configurar nada a mais.

Se quiser um banco separado só pros resgates, preencha
`mongoConnectionString` e `mongoDatabase` na seção `storage`.

O registro do resgate é uma operação atômica (`$addToSet` + `modifiedCount`),
então dois servidores tentando ao mesmo tempo resultam em um só ganhando. Os
comandos da recompensa só rodam depois que o resgate está gravado.

Se o Mongo for pedido e não conectar, os resgates ficam **bloqueados** em vez
de cair pro arquivo local — cair de volta traria o bug de volta em silêncio. O
menu continua abrindo e o erro aparece no log.

## Compilar

Precisa de JDK 21. `gradlew.bat build` — o jar sai em `build/libs/`.
