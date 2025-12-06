package com.example.careerMonitor.DTO;

import com.example.careerMonitor.Entity.CompanyDetails;
import lombok.*;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CompanyResponseDto {

    Long id;

    String name;

    String url;

    String lastCheckedTimeStamp;

    public CompanyResponseDto(CompanyDetails companyDetails){
        this.name = companyDetails.getName();
        this.url= companyDetails.getUrl();
        this.id=companyDetails.getId();
        this.lastCheckedTimeStamp=companyDetails.getLastKnownContentHash();

    }
}
