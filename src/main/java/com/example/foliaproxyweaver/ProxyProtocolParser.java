package com.example.foliaproxyweaver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Utility class that parses HAProxy PROXY protocol headers from a Netty {@link ByteBuf}.
 *
 * <p>The parser supports version 1 (human readable) and version 2 (binary) of
 * the PROXY protocol.  If a header is successfully parsed the parser returns a
 * {@link Result} containing the client and target addresses and the number of
 * bytes consumed.  If no header is present or parsing fails this method
 * returns {@code null} and the caller should rewind the buffer reader index.
 */
public final class ProxyProtocolParser {

    private ProxyProtocolParser() {}

    /** Magic signature for PROXY protocol v2: ``\r\n\r\n\0\r\nQUIT\n``. */
    private static final byte[] V2_MAGIC = new byte[]{
            0x0d, 0x0a, 0x0d, 0x0a, 0x00, 0x0d, 0x0a, 0x51, 0x55, 0x49, 0x54, 0x0a
    };

    /**
     * Parse a PROXY header at the current reader index of a {@link ByteBuf}.
     *
     * @param buf the buffer containing incoming bytes
     * @return a result describing the parsed header or {@code null} if no header
     *         is present
     */
    public static Result parse(ByteBuf buf) {
        if (!buf.isReadable()) {
            return null;
        }
        buf.markReaderIndex();
        try {
            // Check for v2 magic
            if (buf.readableBytes() >= V2_MAGIC.length) {
                boolean magic = true;
                for (int i = 0; i < V2_MAGIC.length; i++) {
                    if (buf.getByte(buf.readerIndex() + i) != V2_MAGIC[i]) {
                        magic = false;
                        break;
                    }
                }
                if (magic) {
                    return parseV2(buf);
                }
            }
            // Check for v1 prefix 'PROXY '
            if (buf.readableBytes() >= 5) {
                byte p0 = buf.getByte(buf.readerIndex());
                byte p1 = buf.getByte(buf.readerIndex() + 1);
                byte p2 = buf.getByte(buf.readerIndex() + 2);
                byte p3 = buf.getByte(buf.readerIndex() + 3);
                byte p4 = buf.getByte(buf.readerIndex() + 4);
                if ((char) p0 == 'P' && (char) p1 == 'R' && (char) p2 == 'O'
                        && (char) p3 == 'X' && (char) p4 == 'Y') {
                    return parseV1(buf);
                }
            }
        } catch (Exception e) {
            // swallow; will fall through to null
        }
        buf.resetReaderIndex();
        return null;
    }

    private static Result parseV1(ByteBuf buf) {
        // v1 header is ascii until CRLF
        int startIdx = buf.readerIndex();
        // Find CRLF
        int lfIndex = ByteBufUtil.indexOf(buf, startIdx, buf.writerIndex(), (byte) '\n');
        if (lfIndex == -1) {
            return null; // header incomplete
        }
        int length = (lfIndex - startIdx) + 1; // include LF
        String headerLine = buf.toString(startIdx, length, CharsetUtil.US_ASCII).trim();
        // Example: PROXY TCP4 192.0.2.1 198.51.100.1 56324 80
        String[] parts = headerLine.split(" ");
        if (parts.length < 6) {
            buf.skipBytes(length);
            return null;
        }
        try {
            String srcIp = parts[2];
            String dstIp = parts[3];
            int srcPort = Integer.parseInt(parts[4]);
            int dstPort = Integer.parseInt(parts[5]);
            buf.skipBytes(length);
            return new Result(new InetSocketAddress(srcIp, srcPort), new InetSocketAddress(dstIp, dstPort), length);
        } catch (NumberFormatException ex) {
            buf.skipBytes(length);
            return null;
        }
    }

    private static Result parseV2(ByteBuf buf) {
        int start = buf.readerIndex();
        // Skip magic signature
        buf.skipBytes(V2_MAGIC.length);
        if (buf.readableBytes() < 4) {
            buf.resetReaderIndex();
            return null;
        }
        // Version/command byte
        byte verCmd = buf.readByte();
        int version = (verCmd & 0xF0) >> 4;
        if (version != 2) {
            buf.resetReaderIndex();
            return null;
        }
        byte famProto = buf.readByte();
        int addressFamily = (famProto & 0xF0) >> 4;
        int transportProto = (famProto & 0x0F);
        int len = buf.readUnsignedShort();
        if (buf.readableBytes() < len) {
            buf.resetReaderIndex();
            return null;
        }
        InetSocketAddress srcAddr = null;
        InetSocketAddress dstAddr = null;
        // For simplicity only parse IPv4 addresses with TCP/UDP
        if (addressFamily == 0x1 && (transportProto == 0x1 || transportProto == 0x2)) {
            // family 1: AF_INET
            byte[] src = new byte[4];
            byte[] dst = new byte[4];
            buf.readBytes(src);
            buf.readBytes(dst);
            int srcPort = buf.readUnsignedShort();
            int dstPort = buf.readUnsignedShort();
            srcAddr = new InetSocketAddress(
                    (src[0] & 0xFF) + "." + (src[1] & 0xFF) + "." + (src[2] & 0xFF) + "." + (src[3] & 0xFF),
                    srcPort);
            dstAddr = new InetSocketAddress(
                    (dst[0] & 0xFF) + "." + (dst[1] & 0xFF) + "." + (dst[2] & 0xFF) + "." + (dst[3] & 0xFF),
                    dstPort);
            // consume remaining TLVs if any
            int consumed = V2_MAGIC.length + 4 + 12; // header + version/command/proto + address block
            int rest = len - 12;
            if (rest > 0) {
                buf.skipBytes(rest);
                consumed += rest;
            }
            return new Result(srcAddr, dstAddr, consumed);
        } else {
            // skip unhandled family
            buf.skipBytes(len);
        }
        return null;
    }

    /**
     * Result of parsing a PROXY protocol header.
     */
    public static final class Result {
        public final InetSocketAddress source;
        public final InetSocketAddress destination;
        public final int bytesConsumed;

        public Result(InetSocketAddress source, InetSocketAddress destination, int bytesConsumed) {
            this.source = source;
            this.destination = destination;
            this.bytesConsumed = bytesConsumed;
        }
    }
}