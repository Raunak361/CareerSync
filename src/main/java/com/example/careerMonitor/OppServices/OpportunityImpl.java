package com.example.careerMonitor.OppServices;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import com.example.careerMonitor.Entity.Opportunity;
import com.example.careerMonitor.repository.OpportunityRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpportunityImpl implements OpportunityService {

    private static final Logger log = LoggerFactory.getLogger(OpportunityImpl.class);

    private final OpportunityRepo opportunityRepo;

    @Override
    public List<OpportunityResponseDto> getAllOpportunityByUserId(String userId) {
        return opportunityRepo.findByCompanyDetails_UserIdAndIsReadFalse(userId)
                .stream()
                .map(OpportunityResponseDto::new)
                .toList();
    }

    @Override
    @Transactional
    public void markOpportunityAsRead(long opportunityId, String userId) {
        Opportunity opportunity = opportunityRepo
                .findByIdAndCompanyDetails_UserId(opportunityId, userId)
                .orElseThrow(() -> new RuntimeException(
                        "Opportunity not found with id=" + opportunityId + " for user=" + userId));

        opportunity.setIsRead(true);
        opportunityRepo.save(opportunity);
        log.info("Marked opportunity id={} as read for user '{}'", opportunityId, userId);
    }
}
