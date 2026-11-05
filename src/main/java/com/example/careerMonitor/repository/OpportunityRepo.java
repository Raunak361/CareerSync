package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.Entity.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OpportunityRepo extends JpaRepository<Opportunity,Long> {

       Set<Opportunity> findByCompanyDetails(CompanyDetails companyDetails);



       // 2. Used by OpportunityServiceImpl (GET): Fetches new (unread) jobs belonging to a user.
       //    findByCompany_UserId navigates the relationship: Opportunity -> Company -> userId
       List<Opportunity> findByCompanyDetails_UserIdAndIsReadFalse(String userId);

       // 3. Used by OpportunityServiceImpl (PUT /read): Securely finds an opportunity by ID and
       //    confirms ownership via the user ID.
       Optional<Opportunity> findByIdAndCompanyDetails_UserId(Long opportunityId, String userId);
}
