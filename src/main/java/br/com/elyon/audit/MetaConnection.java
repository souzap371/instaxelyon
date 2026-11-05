package br.com.elyon.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
class MetaConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String clientName;
    public String metaUserId;
    public String metaUserName;
    @Lob public String encryptedAccessToken;
    public Instant connectedAt = Instant.now();
}
