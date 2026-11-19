package com.example.careerMonitor.OppServices;

import com.example.careerMonitor.DTO.OpportunityResponseDto;
import com.example.careerMonitor.Entity.Opportunity;
import com.example.careerMonitor.Entity.UserCompanyRelation;
import com.example.careerMonitor.repository.OpportunityRepo;
import com.example.careerMonitor.repository.UserCompanyRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpportunityImpl implements OpportunityService {

    private static final Logger log = LoggerFactory.getLogger(OpportunityImpl.class);

    private final OpportunityRepo opportunityRepo;
    private final UserCompanyRepo userCompanyRepo;

    @Override
    public List<OpportunityResponseDto> getAllOpportunityByUserId(String userId) {
        List<UserCompanyRelation> relation = userCompanyRepo.findAllByUserId(userId);
        List<Opportunity> opportunities = new ArrayList<>();
        for(UserCompanyRelation i : relation){
            List<Opportunity> opp = opportunityRepo.findAllByCompanyDetails_Id(i.getCompanyId());
            opportunities.addAll(opp);
        }


        return opportunities
                .stream()
                .map(OpportunityResponseDto::new)
                .toList();
    }

    @Override
    public List<OpportunityResponseDto> getAllOpportunityByUserIdAndExperience(Integer experience, String userId) {
        List<UserCompanyRelation> relation = userCompanyRepo.findAllByUserId(userId);
        List<Opportunity> opportunities = new ArrayList<>();
        for(UserCompanyRelation i : relation){
            List<Opportunity> opp = opportunityRepo.findAllByCompanyDetails_IdAndExperienceLessThanEqual(i.getCompanyId(),experience);
            opportunities.addAll(opp);
        }
        return opportunities
                .stream()
                .map(OpportunityResponseDto::new)
                .toList();
    }

//    @Override
//    @Transactional
//    public void markOpportunityAsRead(long opportunityId, String userId) {
//        Opportunity opportunity = opportunityRepo
//                .findByIdAndCompanyDetails_UserId(opportunityId, userId)
//                .orElseThrow(() -> new RuntimeException(
//                        "Opportunity not found with id=" + opportunityId + " for user=" + userId));
//
//        opportunity.setIsRead(true);
//        opportunityRepo.save(opportunity);
//        log.info("Marked opportunity id={} as read for user '{}'", opportunityId, userId);
//    }
}
