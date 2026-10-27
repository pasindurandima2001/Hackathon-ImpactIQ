package com.impactiq.rag;

import com.impactiq.config.KnowledgeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded originals under a private directory using random names. Files are never served over HTTP.
 * Refuses to start when the storage directory is inside a web root (e.g. XAMPP htdocs).
 */
@Service
public class DocumentStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStorageService.class);
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f\\-]{36}\\.bin$");
    private static final String[] WEB_ROOT_MARKERS = {"htdocs", "www", "public_html", "wwwroot"};

    private final KnowledgeProperties properties;
    private Path root;

    public DocumentStorageService(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        String lower = root.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String marker : WEB_ROOT_MARKERS) {
            if (lower.contains("/" + marker + "/") || lower.endsWith("/" + marker)) {
                throw new IllegalStateException("impactiq.knowledge.storage-dir must be outside any web root: " + root);
            }
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create document storage directory", exception);
        }
        LOGGER.info("Document storage directory ready");
    }

    public String store(Long projectId, byte[] bytes) {
        String storedName = UUID.randomUUID() + ".bin";
        Path target = resolve(projectId, storedName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store uploaded file", exception);
        }
        return storedName;
    }

    public byte[] read(Long projectId, String storedName) {
        try {
            return Files.readAllBytes(resolve(projectId, storedName));
        } catch (IOException exception) {
            throw new UncheckedIOException("Stored file is missing or unreadable", exception);
        }
    }

    public boolean delete(Long projectId, String storedName) {
        try {
            return Files.deleteIfExists(resolve(projectId, storedName));
        } catch (IOException exception) {
            LOGGER.warn("Stored file could not be deleted: category={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private Path resolve(Long projectId, String storedName) {
        if (projectId == null || !STORED_NAME.matcher(storedName).matches()) {
            throw new IllegalArgumentException("Invalid storage reference");
        }
        Path path = root.resolve(String.valueOf(projectId)).resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }
}
