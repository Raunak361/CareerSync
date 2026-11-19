package com.example.careerMonitor.OppServices;

import com.example.careerMonitor.DTO.OpportunityResponseDto;

import java.util.List;

public interface OpportunityService {

    List<OpportunityResponseDto> getAllOpportunityByUserId(String userId);
    List<OpportunityResponseDto> getAllOpportunityByUserIdAndExperience(Integer experience,String userId);

    //void markOpportunityAsRead(long opportunityId,String userId);
}