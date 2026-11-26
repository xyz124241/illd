# RelicAltar (ItemLock + Sense + Altar, verbunden mit ACI)

Ersetzt das alte **ItemLock**-Plugin (nicht beide gleichzeitig laden!). ACI bleibt installiert.
Die alte Sperrliste (`plugins/ItemLock/locked.yml`) wird automatisch uebernommen.

## Befehle (nur OP / `relicaltar.admin`)
| Befehl | Wirkung |
|---|---|
| `/lock` (`/il`, `/sperre`) | Sperr-Menue: suchen, anklicken, **Bestaetigen** |
| `/relic` (`/altar`) | Hauptmenue: Sperre, Altar-Rezepte, Altar & Sense holen |
| `/relic give <altar\|sense\|aci:id\|mc:MATERIAL> [spieler] [anzahl]` | Item geben |
| `/relic reset <item>` | Craft-Zaehler zuruecksetzen |
| `/relic list` | Koordinaten aller Altare |
| `/relic removealtar` | angesehenen Altar entfernen (sonst unabbaubar) |

## Item-Sperre
Gesperrte Items verschwinden aus: Mob-Drops (Enderman!), Truhen/Loot, Trial Vaults, Piglin-Handel, Villager-Trades,
Angeln, Block-Drops, Vanilla-Crafting/Ofen/Schmiede/Amboss, Aufheben. Optional (`remove-from-inventories: true`)
werden sie auch aus Inventaren, Endertruhen und geoeffneten Kisten geloescht.
**Custom-Items (ACI, Sense, Altar) sind nie betroffen.** Plugin-/Altar-Rezepte funktionieren trotz Sperre,
auch fuer gesperrte Items (z.B. Enderperle nur noch per Altar).

## Altar
* Craftbar (Obsidian + Diamantblock + Netherstern, in `config.yml` aenderbar) **und** natuerlich in neuen Chunks zu finden.
* Unabbaubar (auch gegen Explosionen, Kolben, Enderman).
* Rechtsklick -> eigene GUI (3x3 -> Ergebnis, nur Custom-Rezepte).
* Beim Schmieden: Koordinaten werden **an alle Spieler gesendet**, das Item schwebt ueber dem Altar,
  nach `drop-delay-seconds` (300 = 5 Min.) faellt es herunter, leuchtet, und es kommt
  *"New Relic has been crafted at x y z"*. Jeder darf es aufheben.
* Rezepte und Limits im Spiel: `/relic` -> Altar-Rezepte.
  Item auf den Cursor nehmen + Feld anklicken = Zutat/Ergebnis setzen. **Max. Crafts pro Item** und Zaehler-Reset im Editor.
  Ergebnisse koennen Sense, ACI-Items oder normale Items sein.

## Sense
Netherite-Schwert-Basis: Schwert-Schaden, bricht Schilde, Mace-Fallschaden, Schockwelle. Nur noch ueber den Altar craftbar
(Standard-Rezept: Netherite-Axt, -Schwert, Mace oben, Netherstern Mitte; Limit 3).

## Bauen
GitHub: Repo anlegen, alles hochladen, Actions bauen `RelicAltar.jar` + `ResourcePack-ALLES.zip`.
Lokal: `mvn clean package`. Resourcepack = Ordner `resourcepack/` (enthaelt ACI-Kristalle, Sense, Altar-GUI/-Modell).
