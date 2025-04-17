package com.objective.threesixty.agent.filesystem;

/*-
 * %%
 * 3Sixty Remote Agent Example
 * -
 * Copyright (C) 2024 - 2025 Objective Corporation Limited.
 * -
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * %-
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Timestamp;
import com.objective.threesixty.Document;
import com.objective.threesixty.MetadataType;
import com.objective.threesixty.remoteagent.sdk.agent.RepositoryWriter;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import io.micrometer.common.util.StringUtils;
import org.apache.commons.codec.binary.Hex;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@Component
public class FileSystemWriter implements RepositoryWriter {
    @Override
    public Mono<Document> writeDocument(Document doc, Map<String, MetadataType> metadata, Flux<DataBuffer> binaries, CustomParameters params) {
        File outputFile = createOutputFile(doc, params);

        return Mono.fromCallable(() -> ensureFileExists(outputFile))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(file -> writeFileContent(file, binaries, metadata, params, doc))
            .doOnError(e -> getLogger().error("Error processing file: " + outputFile.getPath(), e))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    private File createOutputFile(Document doc, CustomParameters params) {
        return Paths.get(
            params.get("filePath").getString(),
            sanitizePath(doc.getParentPath()),
            doc.getName()
        ).toFile();
    }

    private Mono<Document> writeFileContent(File file, Flux<DataBuffer> binaries, Map<String, MetadataType> metadata, CustomParameters params, Document doc) {
        return Mono.using(
            () -> AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE),
            channel -> DataBufferUtils.write(binaries, channel)
                .onErrorResume(e -> handleErrorDuringWrite(file, e))
                .then(flushFile(channel, file))
                .doFinally(signal -> writeMetadata(file, metadata, params))
                .then(createUpdatedDocument(file, doc)),
            this::closeFileChannel
        );
    }

    private Mono<Object> flushFile(AsynchronousFileChannel channel, File file) {
        return Mono.defer(() -> Mono.fromRunnable(() -> {
            try {
                channel.force(true); // Ensure data is flushed to disk
            } catch (IOException e) {
                throw new UncheckedIOException("Error forcing file channel: " + file.getPath(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Document> createUpdatedDocument(File file, Document doc) {
        return Mono.just(doc.toBuilder()
            .setId(file.getAbsolutePath())
            .setParentPath(file.getParent())
            .build());
    }

    private void closeFileChannel(AsynchronousFileChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            getLogger().error("Error closing file channel", e);
        }
    }

    private Mono<DataBuffer> handleErrorDuringWrite(File file, Throwable e) {
        getLogger().error("Error writing to file: " + file.getPath(), e);
        return Mono.error(e);
    }

    private void writeMetadata(File file, Map<String, MetadataType> metadata, CustomParameters params) {
        try {
            writeMetadataToXml(file, metadata, params);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write metadata for: " + file.getPath(), e);
        }
    }

    private File ensureFileExists(File outputFile) throws IOException {
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("Failed to create directories: " + outputFile.getParentFile().getPath());
        } else if (!outputFile.exists() && !outputFile.createNewFile()) {
            throw new IOException("Failed to create file: " + outputFile.getPath());
        }
        return outputFile;
    }

    private void writeMetadataToXml(File file, Map<String, MetadataType> metadata, CustomParameters params) throws IOException {
        String metadataFilePath = createMetadataFilePath(file, params);
        Properties props = buildMetadataProperties(metadata);

        try (FileOutputStream fos = new FileOutputStream(metadataFilePath)) {
            if (params.get("metadataAsXml").getBoolean()) {
                props.storeToXML(fos, "---No Comment---");
            } else {
                props.store(fos, "---No Comment---");
            }
        }
    }

    private String createMetadataFilePath(File file, CustomParameters params) {
        boolean isXml = params.get("metadataAsXml").getBoolean();
        String fileName = getXMLFileName(file, true, "", "", isXml);
        return file.getParent() + File.separator + fileName;
    }

    private String getXMLFileName(File originalFile, boolean isLatest, String versionLabel, String renditionLabel, boolean metadataAsXML) {
        StringBuilder sb = new StringBuilder(originalFile.getName()).append(".metadata.properties.");

        if (metadataAsXML) {
            sb.append("xml");
        } else {
            sb.append("properties");
        }

        if (!isLatest) {
            sb.append(".v").append(versionLabel);
        }

        if (StringUtils.isNotBlank(renditionLabel)) {
            sb.append(".r").append(renditionLabel);
        }

        return sb.toString();
    }

    private Properties buildMetadataProperties(Map<String, MetadataType> metadata) {
        Properties props = new Properties();
        for (Map.Entry<String, MetadataType> entry : metadata.entrySet()) {
            props.put(entry.getKey(), extractMetadataStringValue(entry.getKey(), entry.getValue()));
        }
        return props;
    }

    @VisibleForTesting
    String extractMetadataStringValue(String key, MetadataType value) {
        if (value.hasArray()) {
            return value.getArray().toString();
        } else if (value.hasBinary()) {
            return Hex.encodeHexString(value.getBinary().toByteArray());
        } else if (value.hasBoolean()) {
            return String.valueOf(value.getBoolean());
        } else if (value.hasDouble()) {
            return String.valueOf(value.getDouble());
        } else if (value.hasDecimal()) {
            return String.valueOf(value.getDecimal());
        } else if (value.hasDateTime()) {
            return convertTimestampToUTCString(value.getDateTime());
        } else if (value.hasInteger()) {
            return String.valueOf(value.getInteger());
        } else if (value.hasLargeString()) {
            return value.getLargeString();
        } else if (value.hasLong()) {
            return String.valueOf(value.getLong());
        } else if (value.hasString()) {
            return value.getString();
        }
        getLogger().warn("Incompatible type. No value found for metadata key " + key);
        return null;
    }

    @VisibleForTesting
    String convertTimestampToUTCString(Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(instant);
    }

    @VisibleForTesting
    String sanitizePath(String path) {
        // Remove any leading/trailing separators and disallowed characters
        Pattern trailingPattern = Pattern.compile("[:/]+$");
        Pattern colonPattern = Pattern.compile(":");

        // Replace using precompiled patterns
        path = trailingPattern.matcher(path).replaceAll(""); // Remove trailing / or :
        path = colonPattern.matcher(path).replaceAll("/");    // Replace colons

        return path;
    }
}
