UPDATE resume_evaluation
SET technical_skills_reason = JSON_ARRAY(technical_skills_reason)
WHERE technical_skills_reason IS NOT NULL
  AND JSON_VALID(technical_skills_reason) = 0;

UPDATE resume_evaluation
SET technical_skills_improvements = JSON_ARRAY(technical_skills_improvements)
WHERE technical_skills_improvements IS NOT NULL
  AND JSON_VALID(technical_skills_improvements) = 0;

UPDATE resume_evaluation
SET project_experience_reason = JSON_ARRAY(project_experience_reason)
WHERE project_experience_reason IS NOT NULL
  AND JSON_VALID(project_experience_reason) = 0;

UPDATE resume_evaluation
SET project_experience_improvements = JSON_ARRAY(project_experience_improvements)
WHERE project_experience_improvements IS NOT NULL
  AND JSON_VALID(project_experience_improvements) = 0;

UPDATE resume_evaluation
SET problem_solving_reason = JSON_ARRAY(problem_solving_reason)
WHERE problem_solving_reason IS NOT NULL
  AND JSON_VALID(problem_solving_reason) = 0;

UPDATE resume_evaluation
SET problem_solving_improvements = JSON_ARRAY(problem_solving_improvements)
WHERE problem_solving_improvements IS NOT NULL
  AND JSON_VALID(problem_solving_improvements) = 0;

UPDATE resume_evaluation
SET career_growth_reason = JSON_ARRAY(career_growth_reason)
WHERE career_growth_reason IS NOT NULL
  AND JSON_VALID(career_growth_reason) = 0;

UPDATE resume_evaluation
SET career_growth_improvements = JSON_ARRAY(career_growth_improvements)
WHERE career_growth_improvements IS NOT NULL
  AND JSON_VALID(career_growth_improvements) = 0;

UPDATE resume_evaluation
SET documentation_reason = JSON_ARRAY(documentation_reason)
WHERE documentation_reason IS NOT NULL
  AND JSON_VALID(documentation_reason) = 0;

UPDATE resume_evaluation
SET documentation_improvements = JSON_ARRAY(documentation_improvements)
WHERE documentation_improvements IS NOT NULL
  AND JSON_VALID(documentation_improvements) = 0;

ALTER TABLE resume_evaluation
    MODIFY COLUMN technical_skills_reason JSON NULL,
    MODIFY COLUMN technical_skills_improvements JSON NULL,
    MODIFY COLUMN project_experience_reason JSON NULL,
    MODIFY COLUMN project_experience_improvements JSON NULL,
    MODIFY COLUMN problem_solving_reason JSON NULL,
    MODIFY COLUMN problem_solving_improvements JSON NULL,
    MODIFY COLUMN career_growth_reason JSON NULL,
    MODIFY COLUMN career_growth_improvements JSON NULL,
    MODIFY COLUMN documentation_reason JSON NULL,
    MODIFY COLUMN documentation_improvements JSON NULL;
