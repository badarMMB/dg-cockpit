package com.dgcockpit.repository;

import com.dgcockpit.entity.TemplateCourrierDepart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemplateCourrierDepartRepository extends JpaRepository<TemplateCourrierDepart, String> {
    List<TemplateCourrierDepart> findAllByOrderByNomAsc();
}
