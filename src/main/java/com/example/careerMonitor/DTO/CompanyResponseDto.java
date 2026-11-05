package com.example.careerMonitor.DTO;

import com.example.careerMonitor.Entity.CompanyDetails;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CompanyResponseDto {

    private Long id;

    private String name;

    private String url;

    private String lastCheckedTimestamp;

    public CompanyResponseDto(CompanyDetails companyDetails) {
        this.name = companyDetails.getName();
        this.url = companyDetails.getUrl();
        this.id = companyDetails.getId();
        this.lastCheckedTimestamp = companyDetails.getLastCheckedTimestamp() != null
                ? companyDetails.getLastCheckedTimestamp().toString()
                : null;
    }
}
