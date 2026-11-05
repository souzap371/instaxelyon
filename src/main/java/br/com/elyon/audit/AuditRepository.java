package br.com.elyon.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface AuditRepository extends JpaRepository<Audit, Long> {
    List<Audit> findTop30ByOrderByCreatedDesc();
}
