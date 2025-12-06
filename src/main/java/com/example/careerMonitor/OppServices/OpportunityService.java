package com.example.careerMonitor.OppServices;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public interface OpportunityService {

    List<OpportunityResponseDto> getAllOpportunityByUserId(String userId);

    void markOpportunityAsRead(long opportunityId,String userId);
}