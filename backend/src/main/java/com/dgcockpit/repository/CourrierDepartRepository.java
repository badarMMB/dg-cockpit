package com.dgcockpit.repository;

import com.dgcockpit.entity.CourrierDepart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourrierDepartRepository extends JpaRepository<CourrierDepart, String> {
    List<CourrierDepart> findAllByOrderByDateEnvoiDesc();
}
