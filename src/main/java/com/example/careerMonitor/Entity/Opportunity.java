package com.example.careerMonitor.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

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
    private String jobUrl;
    // Direct link to the job posting

    @Column(name = "experience", nullable = true, length = 100)
    private String experience;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "detected_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date detectedDate;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;


    public Opportunity(CompanyDetails company, String title, String s, Date date,String experience,String type) {
        this.companyDetails=company;
        this.title= title;
        this.jobUrl=s;
        this.detectedDate=date;
        this.experience = experience;
        this.type = type;
    }
}
