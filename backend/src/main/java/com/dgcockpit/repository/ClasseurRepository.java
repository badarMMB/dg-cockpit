package com.dgcockpit.repository;

import com.dgcockpit.entity.Classeur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClasseurRepository extends JpaRepository<Classeur, String> {
    List<Classeur> findAllByOrderByCreatedAtAsc();
    Optional<Classeur> findByType(Classeur.Type type);
    boolean existsByType(Classeur.Type type);
}
