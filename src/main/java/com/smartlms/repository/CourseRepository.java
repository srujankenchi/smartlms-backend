package com.smartlms.repository;

import com.smartlms.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByPublishedTrue();
    List<Course> findByTeacherId(Long teacherId);
    List<Course> findByPublishedTrueAndTitleContainingIgnoreCase(String title);
    List<Course> findByPublishedTrueAndCategoryIgnoreCase(String category);
    java.util.Optional<Course> findByTitle(String title);
    java.util.Optional<Course> findFirstByTitleIgnoreCase(String title);
    java.util.Optional<Course> findByCode(String code);
    java.util.Optional<Course> findFirstByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
}
