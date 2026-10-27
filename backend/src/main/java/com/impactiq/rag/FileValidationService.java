package com.impactiq.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.config.KnowledgeProperties;
import com.impactiq.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Strict upload validation: size limit, sanitised filename, extension allow-list, and content checks
 * (PDF magic bytes; strict UTF-8 without NUL bytes for text; JSON must parse). The client-supplied
 * content type is never trusted.
 */
@Service
public class FileValidationService {

    private static final int MAX_FILENAME_LENGTH = 150;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public FileValidationService(KnowledgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds the " + (properties.getMaxUploadBytes() / (1024 * 1024)) + " MB limit");
        }
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes((int) properties.getMaxUploadBytes() + 1);
        } catch (IOException exception) {
            throw ApiException.badRequest("The uploaded file could not be read");
        }
        return validate(bytes, file.getOriginalFilename());
    }

    public ValidatedFile validate(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        if (bytes.length > properties.getMaxUploadBytes()) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the allowed size");
        }
        String filename = sanitizeFilename(originalFilename);
        String extension = extensionOf(filename);
        if (extension.isEmpty() || !properties.getAllowedExtensions().contains(extension)) {
            throw ApiException.unsupportedMedia("Unsupported file type. Allowed: " + String.join(", ", properties.getAllowedExtensions()));
        }
        String contentType;
        if ("pdf".equals(extension)) {
            if (!startsWith(bytes, PDF_MAGIC)) {
                throw ApiException.unsupportedMedia("File content is not a valid PDF");
            }
            contentType = "application/pdf";
        } else {
            String text = decodeStrictUtf8(bytes);
            if ("json".equals(extension)) {
                requireValidJson(text);
                contentType = "application/json";
            } else {
                contentType = "text/plain";
            }
        }
        return new ValidatedFile(bytes, filename, extension, contentType, sha256(bytes));
    }

    /** Decodes UTF-8 strictly (BOM stripped). Rejects binary content and NUL bytes. */
    public String decodeStrictUtf8(byte[] bytes) {
        int offset = startsWith(bytes, UTF8_BOM) ? UTF8_BOM.length : 0;
        for (int index = offset; index < bytes.length; index++) {
            if (bytes[index] == 0) {
                throw ApiException.unsupportedMedia("File appears to be binary, not text");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw ApiException.unsupportedMedia("Text files must be UTF-8 encoded");
        }
    }

    static String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            throw ApiException.badRequest("A file name is required");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            String extension = extensionOf(name);
            int keep = MAX_FILENAME_LENGTH - extension.length() - 1;
            name = name.substring(0, keep) + "." + extension;
        }
        return name;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void requireValidJson(String text) {
        try {
            objectMapper.readTree(text);
        } catch (IOException exception) {
            throw ApiException.unsupportedMedia("JSON file is not valid JSON");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
