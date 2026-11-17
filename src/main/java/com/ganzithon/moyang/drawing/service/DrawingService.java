package com.ganzithon.moyang.drawing.service;

import com.ganzithon.moyang.drawing.domain.Drawing;
import com.ganzithon.moyang.drawing.repository.DrawingRepository;

import com.ganzithon.moyang.common.port.Ports.RecordPort;
import com.ganzithon.moyang.common.exception.BusinessException;
import com.ganzithon.moyang.common.exception.ErrorCode;
import com.ganzithon.moyang.drawing.domain.Drawing.InputType;
import com.ganzithon.moyang.common.port.Ports.MeetingSharePort;
import com.ganzithon.moyang.common.port.Ports.TopicReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DrawingService {
    static final long MAX_SIZE = 10L * 1024 * 1024;
    static final int MAX_NOTE = 200;

    private final DrawingRepository drawings;
    private final RecordPort records;
    private final TopicReader topics;
    private final MeetingSharePort shares;
    private final Path dir;
    private final TransactionTemplate tx;

    public DrawingService(DrawingRepository drawings, RecordPort records, TopicReader topics,
                          MeetingSharePort shares, TransactionTemplate tx,
                          @Value("${moyang.upload-dir:./uploads}") String uploadDir) {
        this.tx = tx;
        this.drawings = drawings;
        this.records = records;
        this.topics = topics;
        this.shares = shares;
        this.dir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Drawing upload(long userId, MultipartFile image, String inputType, Long topicId, String note) {
        if (image == null || image.isEmpty() || inputType == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        InputType type;
        try {
            type = InputType.valueOf(inputType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_TYPE);
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > MAX_NOTE) {
            throw new BusinessException(ErrorCode.NOTE_TOO_LONG);
        }
        if (image.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        String contentType = sniffContentType(image);
        if (topicId != null && topics.find(topicId).isEmpty()) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }

        // 파일명은 서버가 만든 UUID만 쓴다 (원본 이름을 경로에 쓰지 않음 → 경로 조작 불가)
        String fileName = UUID.randomUUID() + ("image/png".equals(contentType) ? ".png" : ".jpg");
        Path target = dir.resolve(fileName);
        // 파일을 먼저 쓰고 DB 저장만 짧은 트랜잭션으로 묶는다. 어느 단계든 실패하면 파일을 지운다
        try {
            Files.createDirectories(dir);
            image.transferTo(target);
            return tx.execute(status -> {
                return drawings.save(new Drawing(records.create(userId), userId, topicId, type, cleanNote, fileName,
                        contentType, (int) image.getSize()));
            });
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 고아 파일이 남아도 DB에 없으니 노출되지 않는다
            }
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    /** 본인 그림만. */
    @Transactional(readOnly = true)
    public Drawing getOwned(long userId, long drawingId) {
        Drawing drawing = find(drawingId);
        if (!drawing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return drawing;
    }

    /** 그림 주인 또는 이 그림이 공유된 모임의 참여자. */
    @Transactional(readOnly = true)
    public Drawing getViewable(long userId, long drawingId) {
        Drawing drawing = find(drawingId);
        if (!drawing.getUserId().equals(userId) && !shares.isSharedWith(drawingId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return drawing;
    }

    public Resource loadImage(Drawing drawing) {
        Path path = dir.resolve(drawing.getStoredFileName());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.DRAWING_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    private Drawing find(long drawingId) {
        return drawings.findById(drawingId).orElseThrow(() -> new BusinessException(ErrorCode.DRAWING_NOT_FOUND));
    }

    /**
     * 확장자·Content-Type 헤더는 믿지 않고 파일 앞부분(매직 바이트)으로 판별한다.
     * ponytail: 매직 바이트만 확인. 깨진 이미지까지 걸러야 하면 ImageIO로 전체 디코딩 추가.
     */
    private String sniffContentType(MultipartFile image) {
        byte[] head;
        try (InputStream in = image.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return "image/png";
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
    }
}
