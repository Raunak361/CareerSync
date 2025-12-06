package com.example.careerMonitor.DTO;

import com.example.careerMonitor.Entity.CompanyDetails;

import java.util.Date;

public class OpportunityResponseDto {


    private Long id;

    private Long companyId;


    private CompanyDetails companyDetails;


    private String title;


    private String jobUrl; // Direct link to the job posting


    private Date detectedDate;


    private Boolean isRead = false;
}
