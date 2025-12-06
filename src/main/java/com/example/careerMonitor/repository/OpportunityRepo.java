package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityRepo extends JpaRepository<Opportunity,Long> {

}
