package com.dgcockpit.repository;

import com.dgcockpit.entity.Collaborateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollaborateurRepository extends JpaRepository<Collaborateur, String> {

    @Query("SELECT c FROM Collaborateur c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.role) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Collaborateur> search(@Param("q") String q);
}
