package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.Entity.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface OpportunityRepo extends JpaRepository<Opportunity,Long> {

       List<Opportunity> findAllByCompanyDetails_Id(Long companyId);
       List<Opportunity> findAllByCompanyDetails_IdAndExperienceLessThanEqual(
               Long companyId,
               Integer experience
       );

       Set<Opportunity> findByCompanyDetails(CompanyDetails companyDetails);

}
