package com.example.foliaproxyweaver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Netty handler that inspects the first bytes of an inbound connection and
 * parses HAProxy PROXY protocol headers.  When a valid header is found the
 * handler updates the underlying channel’s remote address via reflection so
 * that downstream code sees the correct client address.
 */
public final class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {

    private final Plugin plugin;
    private final Logger log;
    private boolean parsed = false;

    public ProxyProtocolHandler(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!parsed && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            buf.markReaderIndex();
            ProxyProtocolParser.Result result = ProxyProtocolParser.parse(buf);
            if (result != null) {
                parsed = true;
                InetSocketAddress newAddr = result.source;
                try {
                    // Update the Netty channel remote address.  Many channel
                    // implementations store the remote address in a private
                    // field.  We search for the first InetSocketAddress field
                    // and replace it with our parsed value.
                    Channel ch = ctx.channel();
                    boolean replaced = false;
                    for (Field f : ch.getClass().getDeclaredFields()) {
                        if (!f.getType().isAssignableFrom(InetSocketAddress.class)) continue;
                        f.setAccessible(true);
                        try {
                            Object current = f.get(ch);
                            if (current instanceof InetSocketAddress) {
                                f.set(ch, newAddr);
                                replaced = true;
                                break;
                            }
                        } catch (IllegalAccessException ignored) {
                            // continue
                        }
                    }
                    log.info("[FoliaProxyWeaver] Accepted PROXY header, client=" + newAddr + (replaced ? "" : " (could not replace remote address)") );
                } catch (Exception ex) {
                    log.warning("[FoliaProxyWeaver] Failed to set remote address: " + ex.getMessage());
                }
            } else {
                buf.resetReaderIndex();
            }
        }
        super.channelRead(ctx, msg);
    }

    /**
     * Registers the proxy protocol handler with ProtoWeaver if available.
     *
     * @param plugin plugin instance used for logging
     * @throws ReflectiveOperationException if the registration API is not found
     */
    public static void register(Plugin plugin) throws ReflectiveOperationException {
        Logger log = plugin.getLogger();
        // Try to call ProtoWeaver.registerHandler(String, ChannelInboundHandler)
        Class<?> weaver = Class.forName("me.mrnavastar.protoweaver.ProtoWeaver");
        try {
            // Signature: static void registerHandler(String id, ChannelInboundHandler handler)
            java.lang.reflect.Method m = weaver.getMethod("registerHandler", String.class, io.netty.channel.ChannelInboundHandler.class);
            m.invoke(null, "proxy_protocol", new ProxyProtocolHandler(plugin));
            log.info("[FoliaProxyWeaver] Registered PROXY handler via ProtoWeaver API.");
        } catch (NoSuchMethodException nsme) {
            // fallback: some versions may use different method names
            try {
                java.lang.reflect.Method m = weaver.getMethod("registerNettyHandler", String.class, io.netty.channel.ChannelInboundHandler.class);
                m.invoke(null, "proxy_protocol", new ProxyProtocolHandler(plugin));
                log.info("[FoliaProxyWeaver] Registered PROXY handler via ProtoWeaver (netty) API.");
            } catch (NoSuchMethodException e) {
                // Unavailable API: we cannot register
                throw new ReflectiveOperationException("ProtoWeaver does not expose a handler registration API");
            }
        }
    }
}