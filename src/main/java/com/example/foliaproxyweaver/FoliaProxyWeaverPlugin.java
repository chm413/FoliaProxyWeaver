package com.example.foliaproxyweaver;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * Entry point for the FoliaProxyWeaver plugin.  When the server starts this
 * class checks for the presence of ProtoWeaver and registers the
 * {@link ProxyProtocolHandler}.  If ProtoWeaver is not installed the
 * plugin will disable itself.
 */
public final class FoliaProxyWeaverPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Logger log = getLogger();
        // Verify ProtoWeaver is present on the classpath
        try {
            Class.forName("me.mrnavastar.protoweaver.ProtoWeaver");
        } catch (ClassNotFoundException e) {
            log.warning("ProtoWeaver was not found on the classpath.  " +
                    "This plugin depends on ProtoWeaver to hook into the Netty pipeline.  " +
                    "Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Attempt to register our proxy handler
        try {
            ProxyProtocolHandler.register(this);
            log.info("FoliaProxyWeaver has been enabled and is listening for PROXY headers.");
        } catch (Throwable t) {
            log.severe("Failed to register ProxyProtocolHandler: " + t.getMessage());
            t.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}