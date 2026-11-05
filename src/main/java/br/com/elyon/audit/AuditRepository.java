package br.com.elyon.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

interface AuditRepository extends JpaRepository<Audit, Long> {
    Page<Audit> findAllByOrderByCreatedDesc(Pageable pageable);
}
