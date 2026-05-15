package com.dgcockpit.repository;

import com.dgcockpit.entity.CourrierArrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourrierArriveRepository extends JpaRepository<CourrierArrive, String> {
    List<CourrierArrive> findAllByOrderByDateReceptionDesc();
    long countByStatut(CourrierArrive.Statut statut);

    @Query("SELECT c FROM CourrierArrive c WHERE LOWER(c.objet) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.expediteur) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.apercu) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<CourrierArrive> search(@Param("q") String q);
}
