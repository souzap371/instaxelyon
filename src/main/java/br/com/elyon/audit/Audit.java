package br.com.elyon.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Audit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String username;
    public Instant created = Instant.now();
    public String source;
    @Lob
    public String evidence;
    @Lob
    public String report;
    @Lob
    public String screenshot;
    @Lob
    public String profileImage;
    public Long followerCount;
    public Long followingCount;
    public String status;
    public String error;
}
