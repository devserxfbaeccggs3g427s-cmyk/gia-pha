package vn.giapha.research.audit.support;

import java.security.SecureRandom;

public final class Ids {
    private static final char[] ALPHABET =
            "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict".toCharArray();
    private static final int LENGTH = 21;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String newId() {
        byte[] bytes = new byte[LENGTH];
        RANDOM.nextBytes(bytes);
        StringBuilder id = new StringBuilder(LENGTH);
        for (byte value : bytes) {
            id.append(ALPHABET[value & 0x3f]);
        }
        return id.toString();
    }
}
