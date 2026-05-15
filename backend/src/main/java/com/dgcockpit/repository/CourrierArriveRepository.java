package com.dgcockpit.repository;

import com.dgcockpit.entity.CourrierArrive;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourrierArriveRepository extends JpaRepository<CourrierArrive, String> {
    List<CourrierArrive> findAllByOrderByDateReceptionDesc();
}
