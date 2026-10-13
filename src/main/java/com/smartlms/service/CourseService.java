package com.smartlms.service;

import com.smartlms.dto.CourseRequest;
import com.smartlms.dto.CourseResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CourseResponse> getAllPublishedCourses() {
        return courseRepository.findByPublishedTrue().stream()
                .map(this::mapToCourseResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> searchPublishedCourses(String keyword) {
        return courseRepository.findByPublishedTrueAndTitleContainingIgnoreCase(keyword).stream()
                .map(this::mapToCourseResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getPublishedCoursesByCategory(String category) {
        return courseRepository.findByPublishedTrueAndCategoryIgnoreCase(category).stream()
                .map(this::mapToCourseResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        if (!course.isPublished()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                User user = userRepository.findByEmail(auth.getName()).orElse(null);
                if (user == null || (user.getRole() != Role.ROLE_ADMIN && !course.getTeacher().getId().equals(user.getId()))) {
                    throw new ResourceNotFoundException("Course", "id", id);
                }
            } else {
                throw new ResourceNotFoundException("Course", "id", id);
            }
        }

        return mapToCourseResponse(course);
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getTeacherCourses(String teacherEmail) {
        User teacher = getUserByEmail(teacherEmail);
        return courseRepository.findByTeacherId(teacher.getId()).stream()
                .map(this::mapToCourseResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseResponse createCourse(CourseRequest request, String teacherEmail) {
        User teacher = getUserByEmail(teacherEmail);

        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = request.getTitle().toUpperCase().replaceAll("[^A-Z0-9]+", "-");
            if (code.length() > 50) {
                code = code.substring(0, 50);
            }
        }

        Course course = Course.builder()
                .code(code)
                .title(request.getTitle())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .category(request.getCategory())
                .published(request.isPublished())
                .active(request.isActive())
                .teacher(teacher)
                .build();

        Course savedCourse = courseRepository.save(course);
        return mapToCourseResponse(savedCourse);
    }

    @Transactional
    public CourseResponse updateCourse(Long id, CourseRequest request, String teacherEmail) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = getUserByEmail(teacherEmail);
        validateOwnershipOrAdmin(course, user);

        if (request.getCode() != null && !request.getCode().isBlank()) {
            course.setCode(request.getCode());
        }
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(request.getThumbnailUrl());
        course.setCategory(request.getCategory());
        course.setPublished(request.isPublished());
        course.setActive(request.isActive());

        Course updatedCourse = courseRepository.save(course);
        return mapToCourseResponse(updatedCourse);
    }

    @Transactional
    public void deleteCourse(Long id, String requestorEmail) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = getUserByEmail(requestorEmail);
        validateOwnershipOrAdmin(course, user);

        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByOrderIndexAsc(course.getId());
        lessonRepository.deleteAll(lessons);

        courseRepository.delete(course);
    }

    @Transactional
    public CourseResponse publishCourse(Long id, String teacherEmail) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = getUserByEmail(teacherEmail);
        validateOwnershipOrAdmin(course, user);

        course.setPublished(true);
        Course updatedCourse = courseRepository.save(course);
        return mapToCourseResponse(updatedCourse);
    }

    @Transactional
    public CourseResponse unpublishCourse(Long id, String teacherEmail) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = getUserByEmail(teacherEmail);
        validateOwnershipOrAdmin(course, user);

        course.setPublished(false);
        Course updatedCourse = courseRepository.save(course);
        return mapToCourseResponse(updatedCourse);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private void validateOwnershipOrAdmin(Course course, User user) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return;
        }
        if (!course.getTeacher().getId().equals(user.getId())) {
            throw new BadRequestException("You do not have permission to modify this course");
        }
    }

    private CourseResponse mapToCourseResponse(Course course) {
        int lessonCount = lessonRepository.countByCourseId(course.getId());
        return CourseResponse.fromEntity(course, lessonCount);
    }
}
