package com.dgcockpit.repository;

import com.dgcockpit.entity.Poste;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PosteRepository extends JpaRepository<Poste, String> {

    Optional<Poste> findByCode(String code);

    List<Poste> findByActifTrueOrderByLibelleAsc();

    List<Poste> findAllByOrderByLibelleAsc();
}
