package com.dgcockpit.repository;

import com.dgcockpit.entity.CourrierDepart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourrierDepartRepository extends JpaRepository<CourrierDepart, String> {
    List<CourrierDepart> findAllByOrderByDateEnvoiDesc();
    long countByStatut(CourrierDepart.Statut statut);

    @Query("SELECT c FROM CourrierDepart c WHERE LOWER(c.objet) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.destinataire) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.apercu) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<CourrierDepart> search(@Param("q") String q);
}
