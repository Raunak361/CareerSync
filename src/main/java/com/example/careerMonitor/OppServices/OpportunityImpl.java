package com.example.careerMonitor.OppServices;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import com.example.careerMonitor.repository.OpportunityRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpportunityImpl implements OpportunityService {

    @Autowired
    private final OpportunityRepo opportunityRepo;


    @Override
    public List<OpportunityResponseDto> getAllOpportunityByUserId(String userId) {
        // to be implemented later
        return List.of();
    }

    @Override
    public void markOpportunityAsRead(long opportunityId, String userId) {

        //to be implemented later
    }
}
