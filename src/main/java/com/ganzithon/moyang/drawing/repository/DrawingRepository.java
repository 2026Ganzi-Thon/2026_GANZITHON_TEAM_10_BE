package com.ganzithon.moyang.drawing.repository;

import com.ganzithon.moyang.drawing.domain.Drawing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {
    Optional<Drawing> findByRecordId(Long recordId);
}
