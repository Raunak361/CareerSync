package com.example.careerMonitor.Entity;

import java.util.Set;
import java.util.Date;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "company")
public class CompanyDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user ID associated with this company list (for security)
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    // Hash of the last successfully scraped content (used for change detection)
    @Column(name = "last_known_content_hash", length = 64)
    private String lastKnownContentHash;

    @Column(name = "last_checked_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastCheckedTimestamp;

    // Relationship: One company can have many found opportunities
    // 'mappedBy' indicates the field in the Opportunity class that owns the relationship
    @OneToMany(mappedBy = "companyDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Opportunity> opportunities;

}