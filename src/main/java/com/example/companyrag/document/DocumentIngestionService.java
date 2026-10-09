package com.example.companyrag.document;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx", "md", "txt", "html", "htm");

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final long maxFileSize;

    public DocumentIngestionService(VectorStore vectorStore,
                                    @Value("${rag.ingestion.chunk-size:800}") int chunkSize,
                                    @Value("${rag.ingestion.min-chunk-size-chars:200}") int minChunkSizeChars,
                                    @Value("${rag.ingestion.min-chunk-length-to-embed:10}") int minChunkLengthToEmbed,
                                    @Value("${rag.ingestion.max-file-size-bytes:20971520}") long maxFileSize) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(minChunkLengthToEmbed)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
        this.maxFileSize = maxFileSize;
    }

    public IngestionResult ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("请上传非空文档");
        }
        if (file.getSize() > maxFileSize) {
            throw new InvalidDocumentException("文件大小不能超过 20 MB");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new InvalidDocumentException("暂不支持该文件类型，请上传 PDF、Word、Markdown、TXT 或 HTML 文件");
        }

        try {
            byte[] bytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            List<Document> extracted = new TikaDocumentReader(resource).read();
            if (extracted.isEmpty() || extracted.stream().allMatch(document -> document.getText() == null || document.getText().isBlank())) {
                throw new InvalidDocumentException("未能从文件中提取到可索引的文本");
            }

            String documentId = UUID.randomUUID().toString();
            Map<String, Object> baseMetadata = new HashMap<>();
            baseMetadata.put("documentId", documentId);
            baseMetadata.put("source", filename);
            baseMetadata.put("uploadedAt", Instant.now().toString());
            List<Document> withMetadata = extracted.stream()
                    .map(document -> new Document(document.getText(), merge(document.getMetadata(), baseMetadata)))
                    .toList();
            List<Document> split = splitter.apply(withMetadata);
            List<Document> indexed = new ArrayList<>(split.size());
            for (int i = 0; i < split.size(); i++) {
                Document chunk = split.get(i);
                Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                metadata.put("chunkIndex", i);
                indexed.add(new Document(chunk.getText(), metadata));
            }
            if (indexed.isEmpty()) {
                throw new InvalidDocumentException("文档内容太短，无法生成有效分块");
            }
            vectorStore.add(indexed);
            return new IngestionResult(documentId, filename, indexed.size(), "已完成文档解析、分块、向量化和入库");
        } catch (IOException exception) {
            throw new InvalidDocumentException("读取上传文件失败");
        }
    }

    private static Map<String, Object> merge(Map<String, Object> original, Map<String, Object> added) {
        Map<String, Object> metadata = new HashMap<>(original);
        metadata.putAll(added);
        return metadata;
    }

    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new InvalidDocumentException("文件名不能为空");
        }
        String filename = original.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || filename.length() > 180) {
            throw new InvalidDocumentException("文件名无效或过长");
        }
        return filename;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record IngestionResult(String documentId, String filename, int chunkCount, String message) {}

    public static class InvalidDocumentException extends RuntimeException {
        public InvalidDocumentException(String message) {
            super(message);
        }
    }
}
