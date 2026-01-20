package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.resume.domain.MemberResume;

public record ResumeInfo(String name, String url) {

    public static ResumeInfo fromNullable(MemberResume resume) {
        if (resume == null) {
            return null;
        }
        return new ResumeInfo(resume.getTitle(), resume.getResumeUrl());
    }
}
