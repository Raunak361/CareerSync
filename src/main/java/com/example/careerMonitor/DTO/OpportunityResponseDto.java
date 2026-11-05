package com.example.careerMonitor.DTO;

import com.example.careerMonitor.Entity.Opportunity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OpportunityResponseDto {

    private Long id;

    private Long companyId;

    private String companyName;

    private String title;

    private String type;

    private String jobUrl;

    private String experience;

    private Date detectedDate;

    private Boolean isRead;

    /**
     * Maps an Opportunity entity to this DTO.
     * Replaces the raw CompanyDetails entity with only the fields the API consumer needs.
     */
    public OpportunityResponseDto(Opportunity opportunity) {
        this.id = opportunity.getId();
        this.companyId = opportunity.getCompanyDetails().getId();
        this.companyName = opportunity.getCompanyDetails().getName();
        this.title = opportunity.getTitle();
        this.jobUrl = opportunity.getJobUrl();
        this.detectedDate = opportunity.getDetectedDate();
        this.isRead = opportunity.getIsRead();
        this.experience = opportunity.getExperience();
        this.type = opportunity.getType();
    }
}
