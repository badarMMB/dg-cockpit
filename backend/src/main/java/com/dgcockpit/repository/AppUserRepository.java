package com.dgcockpit.repository;

import com.dgcockpit.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findAllByOrderByNomCompletAsc();
}
