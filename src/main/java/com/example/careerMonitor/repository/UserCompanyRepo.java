package com.example.careerMonitor.repository;

import com.example.careerMonitor.Entity.UserCompanyRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCompanyRepo extends JpaRepository<UserCompanyRelation,Long> {

    List<UserCompanyRelation> findAllByUserId(String userId);
    UserCompanyRelation findByUserIdAndCompanyId(String userID,Long companyId);
    boolean existsByUserIdAndCompanyId(String userId, Long companyId);

}
