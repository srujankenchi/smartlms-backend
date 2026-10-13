package com.smartlms.controller;

import com.smartlms.dto.ApiResponse;
import com.smartlms.dto.CourseRequest;
import com.smartlms.dto.CourseResponse;
import com.smartlms.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getAllCourses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        List<CourseResponse> courses;
        if (search != null && !search.trim().isEmpty()) {
            courses = courseService.searchPublishedCourses(search.trim());
        } else if (category != null && !category.trim().isEmpty()) {
            courses = courseService.getPublishedCoursesByCategory(category.trim());
        } else {
            courses = courseService.getAllPublishedCourses();
        }
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved successfully", courses));
    }

    @GetMapping("/teacher/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getMyCourses(Authentication authentication) {
        List<CourseResponse> courses = courseService.getTeacherCourses(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Teacher courses retrieved successfully", courses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourseById(@PathVariable Long id) {
        CourseResponse course = courseService.getCourseById(id);
        return ResponseEntity.ok(ApiResponse.success("Course retrieved successfully", course));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CourseRequest request,
            Authentication authentication) {
        CourseResponse course = courseService.createCourse(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully", course));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseRequest request,
            Authentication authentication) {
        CourseResponse course = courseService.updateCourse(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", course));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable Long id,
            Authentication authentication) {
        courseService.deleteCourse(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully"));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
            @PathVariable Long id,
            Authentication authentication) {
        CourseResponse course = courseService.publishCourse(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Course published successfully", course));
    }

    @PutMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> unpublishCourse(
            @PathVariable Long id,
            Authentication authentication) {
        CourseResponse course = courseService.unpublishCourse(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Course unpublished successfully", course));
    }
}
