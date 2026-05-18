package com.example.phm.vibration.service;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.example.phm.config.StorageProperties;
import com.example.phm.vibration.dto.VibrationWindowMessage;
import org.springframework.stereotype.Service;

@Service
public class RawWindowFileStorageService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path rawWindowDir;

    public RawWindowFileStorageService(StorageProperties properties) {
        this.rawWindowDir = Path.of(properties.rawWindowDir());
    }

    public String save(VibrationWindowMessage message, String rawPayload, LocalDateTime measuredAt) {
        try {
            String equipmentCode = sanitizePathPart(message.getEquipmentId());
            Path directory = rawWindowDir
                    .resolve(equipmentCode)
                    .resolve(measuredAt.toLocalDate().toString());
            Files.createDirectories(directory);

            Path target = directory.resolve(buildFileName(message, measuredAt));
            Files.writeString(
                    target,
                    withTrailingNewline(rawPayload),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            return target.toAbsolutePath().normalize().toString();
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to save raw vibration window file", exception);
        }
    }

    public long deleteAll() {
        if (!Files.exists(rawWindowDir)) {
            return 0L;
        }

        AtomicLong deletedFileCount = new AtomicLong();
        try (Stream<Path> paths = Files.walk(rawWindowDir)) {
            paths
                    .filter(path -> !path.equals(rawWindowDir))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> deletePath(path, deletedFileCount));
            Files.createDirectories(rawWindowDir);
            return deletedFileCount.get();
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to clear raw vibration window files", exception);
        }
    }

    private void deletePath(Path path, AtomicLong deletedFileCount) {
        try {
            if (Files.isRegularFile(path)) {
                deletedFileCount.incrementAndGet();
            }
            Files.deleteIfExists(path);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to delete raw window path: " + path, exception);
        }
    }

    private String buildFileName(VibrationWindowMessage message, LocalDateTime measuredAt) {
        String timestamp = measuredAt.format(TIMESTAMP_FORMAT);
        long windowIndex = message.getWindowIndex() == null ? -1L : message.getWindowIndex();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return String.format("window_%06d_%s_%s.json", windowIndex, timestamp, suffix);
    }

    private String sanitizePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String withTrailingNewline(String value) {
        if (value.endsWith("\n")) {
            return value;
        }
        return value + "\n";
    }
}
