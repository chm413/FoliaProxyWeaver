# FoliaProxyWeaver

FoliaProxyWeaver is a Bukkit/Spigot/Folia plugin designed to restore the real
client IP address when a Minecraft server is sitting behind a TCP proxy (such
as frp, HAProxy or Nginx) that speaks the HAProxy ``PROXY`` protocol.  In
typical reverse proxy scenarios the backend server only sees the proxy’s
address, causing authentication and moderation plugins (for example AuthMe or
Plan) to misidentify all players as connecting from the same host.  This
project hooks into the Netty channel used by Folia/Paper and uses a
light‑weight parser to detect and strip ``PROXY`` protocol headers before
Minecraft traffic is processed.  Once the header is parsed the plugin uses
reflection to update the internal ``NetworkManager`` so that downstream API
calls (``Player#getAddress()``) return the correct client IP.

### Features

* **Automatic PROXY header parsing** – Supports both version 1 (human
  readable) and version 2 (binary) of the HAProxy protocol.
* **Safe fallback behaviour** – If no PROXY header is detected the handler
  simply forwards the bytes without modification.
* **Folia compatibility** – The plugin has been designed to be safe in
  Folia’s region‑based threading model.  All Netty operations run on the
  network thread and reflective updates are scheduled on the region thread.
* **Configurable trusted proxies** – Only connections originating from
  configured proxy addresses are allowed to supply PROXY headers to prevent
  spoofing.

### Building

This repository uses Gradle.  To build the plugin run:

```bash
./gradlew build
```

The shaded JAR will be produced under `build/libs/`.  Do not relocate or
shadow ProtoWeaver; it must be installed alongside your server.

### Installation

1. Copy the built JAR into your server’s `plugins` directory.
2. Ensure that [ProtoWeaver](https://github.com/MrNavaStar/protoweaver) is
   installed on your server (the plugin depends on the Netty API it
   exposes).
3. Edit the generated `config.yml` to list your trusted proxy addresses.
4. Restart the server.  When enabled the plugin will log each parsed
   PROXY header.

### Disclaimer

ProtoWeaver itself does not ship with any explicit licence at the time of
writing【639627431146389†L200-L264】.  Before redistributing this project or
modifying ProtoWeaver please contact the original author for licence
clarification.  This plugin simply depends on the runtime API provided by
ProtoWeaver and does not embed any of its code.