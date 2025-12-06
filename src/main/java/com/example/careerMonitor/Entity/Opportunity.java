package com.example.careerMonitor.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relationship: Many opportunities belong to one company
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyDetails companyDetails;

    @Column(name = "title", nullable = false, length = 255)
    private String title;


    @Column(name = "job_url", nullable = false, length = 500)
    private String jobUrl; // Direct link to the job posting

    @Column(name = "detected_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date detectedDate;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

}
