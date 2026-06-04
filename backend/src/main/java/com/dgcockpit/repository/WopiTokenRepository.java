package com.dgcockpit.repository;

import com.dgcockpit.entity.WopiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface WopiTokenRepository extends JpaRepository<WopiToken, String> {

    Optional<WopiToken> findByToken(String token);

    @Transactional
    void deleteByBureauDocumentId(String bureauDocumentId);

    @Transactional
    void deleteByExpiresAtBefore(Instant cutoff);
}
