package vn.giapha.research.tree.infrastructure.kernel.id;

import java.security.SecureRandom;

public final class Ids {
    private static final char[] ALPHABET =
            "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static String newId() {
        byte[] bytes = new byte[21];
        RANDOM.nextBytes(bytes);
        StringBuilder id = new StringBuilder(21);
        for (byte value : bytes) {
            id.append(ALPHABET[value & 0x3F]);
        }
        return id.toString();
    }
}
