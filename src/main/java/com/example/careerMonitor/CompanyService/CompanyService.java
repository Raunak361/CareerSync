package com.example.careerMonitor.CompanyService;

import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import java.util.List;


public interface CompanyService {

    List<CompanyResponseDto> getAllCompaniesByUserId(String userId);

    List<CompanyResponseDto> addCompany(String userId, CompanyRequestDto companyRequestDto);

    boolean deleteCompany(long companyId , String userId);

    List<CompanyResponseDto> getAllCompanies();
}
