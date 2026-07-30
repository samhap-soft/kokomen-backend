package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CareerMaterialsFacadeService {

    private final CareerMaterialsService careerMaterialsService;

    public CareerMaterialsResponse getCareerMaterials(CareerMaterialsType type, MemberAuth memberAuth) {
        return careerMaterialsService.getCareerMaterials(type, memberAuth);
    }
}
