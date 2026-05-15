package com.dgcockpit.repository;

import com.dgcockpit.entity.RendezVous;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface RendezVousRepository extends JpaRepository<RendezVous, String> {
    List<RendezVous> findAllByOrderByHeureAsc();
    List<RendezVous> findByDateBetweenOrderByDateAscHeureAsc(LocalDate start, LocalDate end);

    @Query("SELECT r FROM RendezVous r WHERE LOWER(r.titre) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(r.visiteur) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(r.organisation) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<RendezVous> search(@Param("q") String q);
}
