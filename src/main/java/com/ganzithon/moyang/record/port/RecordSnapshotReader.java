package com.ganzithon.moyang.record.port;

import com.ganzithon.moyang.record.dto.RecordDtos.Snapshot;
import java.util.Optional;

/**
 * Drawing/AI integration contract. Adapts the server-side RecordView for completion;
 * return empty when the drawing/card is missing or is not ready. Never accept
 * client-supplied analysis as the authoritative snapshot.
 */
public interface RecordSnapshotReader {
    Optional<Snapshot> find(long recordId);
}
