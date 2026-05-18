package com.dgcockpit.repository;

import com.dgcockpit.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, String> {
    Optional<UserToken> findByTokenAndValidTrue(String token);
    Optional<UserToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("UPDATE UserToken t SET t.valid = false WHERE t.user.id = :userId AND t.valid = true")
    void invalidateAllForUser(@Param("userId") String userId);
}
