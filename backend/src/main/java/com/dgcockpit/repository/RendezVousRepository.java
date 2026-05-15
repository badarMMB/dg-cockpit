package com.dgcockpit.repository;

import com.dgcockpit.entity.RendezVous;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RendezVousRepository extends JpaRepository<RendezVous, String> {
    List<RendezVous> findAllByOrderByHeureAsc();
}
