package com.example.quizlet.repository;

import com.example.quizlet.entity.StudyAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyAttemptRepository extends JpaRepository<StudyAttempt, Long> {
}
