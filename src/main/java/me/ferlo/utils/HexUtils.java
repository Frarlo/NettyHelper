package me.ferlo.utils;

import java.util.StringJoiner;

public final class HexUtils {

    private HexUtils() {}

    private static final char[] HEXCODE = "0123456789ABCDEF".toCharArray();

    public static String getHex(byte[] data) {
        return getHex(data, new StringJoiner(", "));
    }

    public static String getHex(Byte[] data) {
        return getHex(data, new StringJoiner(", "));
    }

    public static String getHex(byte[] data, StringJoiner sj) {
        for (byte b : data)
            sj.add(getHex(b));
        return sj.toString();
    }

    public static String getHex(Byte[] data, StringJoiner sj) {
        for (byte b : data)
            sj.add(getHex(b));
        return sj.toString();
    }

    public static String getHex(byte b) {
        return "0x" + HEXCODE[(b >> 4) & 0xF] + HEXCODE[(b & 0xF)];
    }
}
