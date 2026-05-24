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
