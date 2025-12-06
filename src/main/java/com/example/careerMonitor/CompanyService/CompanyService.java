package com.example.careerMonitor.CompanyService;

import com.example.careerMonitor.DTO.CompanyRequestDto;
import com.example.careerMonitor.DTO.CompanyResponseDto;
import org.springframework.stereotype.Service;
import java.util.List;

@Service


public interface CompanyService {

    List<CompanyResponseDto> getAllCompaniesByUserId(String userId);

    CompanyResponseDto addCompany(String userId, CompanyRequestDto companyRequestDto);

    boolean deleteCompany(long companyId , String userId);
}
