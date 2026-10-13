package com.smartlms.service;

import com.smartlms.dto.LessonRequest;
import com.smartlms.dto.LessonResponse;
import com.smartlms.entity.Course;
import com.smartlms.entity.Lesson;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.exception.BadRequestException;
import com.smartlms.exception.ResourceNotFoundException;
import com.smartlms.repository.CourseRepository;
import com.smartlms.repository.LessonRepository;
import com.smartlms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LessonResponse> getLessonsByCourseId(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
        return lessonRepository.findByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .map(LessonResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LessonResponse getLessonById(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        return LessonResponse.fromEntity(lesson);
    }

    @Transactional
    public LessonResponse createLesson(Long courseId, LessonRequest request, String teacherEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        User user = getUserByEmail(teacherEmail);
        validateCourseOwnershipOrAdmin(course, user);

        Lesson lesson = Lesson.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .content(request.getContent())
                .videoUrl(request.getVideoUrl())
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0)
                .course(course)
                .build();

        Lesson savedLesson = lessonRepository.save(lesson);
        return LessonResponse.fromEntity(savedLesson);
    }

    @Transactional
    public LessonResponse updateLesson(Long id, LessonRequest request, String teacherEmail) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        User user = getUserByEmail(teacherEmail);
        validateCourseOwnershipOrAdmin(lesson.getCourse(), user);

        lesson.setTitle(request.getTitle());
        lesson.setDescription(request.getDescription());
        lesson.setContent(request.getContent());
        lesson.setVideoUrl(request.getVideoUrl());
        if (request.getOrderIndex() != null) {
            lesson.setOrderIndex(request.getOrderIndex());
        }

        Lesson updatedLesson = lessonRepository.save(lesson);
        return LessonResponse.fromEntity(updatedLesson);
    }

    @Transactional
    public void deleteLesson(Long id, String teacherEmail) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        User user = getUserByEmail(teacherEmail);
        validateCourseOwnershipOrAdmin(lesson.getCourse(), user);

        lessonRepository.delete(lesson);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private void validateCourseOwnershipOrAdmin(Course course, User user) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return;
        }
        if (!course.getTeacher().getId().equals(user.getId())) {
            throw new BadRequestException("You do not have permission to modify lessons for this course");
        }
    }
}
