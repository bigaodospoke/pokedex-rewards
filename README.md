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

## Onde ficam os resgates

`mundo/pokedexrewards/claims.json`. Salva a cada resgate e no desligamento.

## Compilar

Precisa de JDK 21. `gradlew.bat build` — o jar sai em `build/libs/`.

O build não funciona com o projeto dentro do OneDrive (o Kotlin não consegue
limpar o cache). Mantenha o projeto fora dele, tipo `C:\dev\pokedex-rewards`.
