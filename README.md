# skript-holo *(client side display holograms)*
This fork of skript-holo no longer depends on a hologram plugin. Holograms are made of client side
display entities (text, item and interaction displays) sent with [PacketEvents](https://modrinth.com/plugin/packetevents):
nothing is spawned in the world, nothing is saved to disk and nothing ticks on the server.

### Requirements
- Paper 1.21.x or 26.x (Java 21+)
- Skript 2.15+
- PacketEvents 2.12.1+ (DecentHolograms, HolographicDisplays and ProtocolLib are not needed anymore)

### How it works
- A standing hologram is sent to the players that received its chunk and removed when they unload it.
- A hologram that follows an entity with a vertical offset (e.g. `offset by direction 2 meters above`) rides the
  entity as a passenger: the client moves it, so it is perfectly smooth and costs nothing on the server.
  Other offsets (e.g. `1 meter infront`) and click-able lines move the hologram once per tick instead.
- A following hologram is deleted when its entity dies or is removed, and keeps following it when the entity is
  unloaded with its chunk and loaded again.
- Click-able, touchable or interactive lines fire the `on hologram click` event (`on hologram left/right click` also work),
  with `event-player`, `event-hologram` and `event-hologram-line`.
- Text supports `&` color codes, `&#RRGGBB` hex colors and Skript's own color tags.

### Scripts using skript-reflect
Scripts that reached into DecentHolograms objects keep working for the common calls:
`({_holo}).getPage(0)`, `({_page}).setLine(index, text)`, `({_page}).lines.size()`.
Calls to `DHAPI` itself need DecentHolograms and do not work on these holograms.

For more info, please
<br>[![SkriptHubViewTheDocs](https://skripthub.net/static/addon/ViewTheDocsButton.png)](https://skripthub.net/docs/?addon=skript-holo), you will find everything there.

### Disclaimer
Do not contact Blueyescat about this fork.
