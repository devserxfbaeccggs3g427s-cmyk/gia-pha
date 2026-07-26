package vn.giapha.research.binarystorage.application.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import vn.giapha.research.binarystorage.shared.error.ValidationException;

/**
 * Deep content validation (Task 15.4, Req 7): the declared MIME type, the
 * magic bytes and — for raster images — an actual parse must all agree before
 * anything is promoted out of quarantine. The allowlist is frozen to the
 * legacy set (JPEG/PNG/WebP/PDF, domain-semantics §5); anything else is
 * rejected regardless of what it contains.
 *
 * <p>Failures throw {@link ValidationException} with a generic, content-free
 * message: validation details never leak file contents or paths.
 */
@Component
public class ContentValidator {

    /** Frozen legacy media allowlist. */
    public static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    /**
     * @param declaredMimeType the type the intent promised (already allowlisted at request time)
     * @param bytes            the quarantined object, fully fetched and hash-verified
     * @throws ValidationException when type, magic bytes and parse do not agree
     */
    public void validate(String declaredMimeType, byte[] bytes) {
        String mime = declaredMimeType == null ? "" : declaredMimeType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(mime)) {
            throw new ValidationException("Unsupported content type");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("Empty content");
        }
        boolean magicOk = switch (mime) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> isWebp(bytes);
            case "application/pdf" -> startsWith(bytes, '%', 'P', 'D', 'F', '-');
            default -> false;
        };
        if (!magicOk) {
            throw new ValidationException("Content does not match its declared type");
        }
        // Raster parse (defense against polyglots/decompression tricks). The JDK
        // ships JPEG and PNG readers; WebP has none, so magic bytes plus the RIFF
        // length check above are the deepest structural check available here.
        if (mime.equals("image/jpeg") || mime.equals("image/png")) {
            parseImage(bytes);
        }
    }

    private static void parseImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new ValidationException("Image failed to parse");
            }
        } catch (IOException e) {
            throw new ValidationException("Image failed to parse");
        }
    }

    private static boolean isWebp(byte[] bytes) {
        // RIFF <u32 length> WEBP — verify both fourCCs and that the declared
        // RIFF payload length is consistent with the actual size.
        if (bytes.length < 12
                || !startsWith(bytes, 'R', 'I', 'F', 'F')
                || bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            return false;
        }
        long declared = (bytes[4] & 0xFFL) | (bytes[5] & 0xFFL) << 8
                | (bytes[6] & 0xFFL) << 16 | (bytes[7] & 0xFFL) << 24;
        return declared + 8 <= bytes.length + 1L; // +1: RIFF pads odd sizes
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != (prefix[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
