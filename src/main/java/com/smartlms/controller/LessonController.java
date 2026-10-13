package com.smartlms.controller;

import com.smartlms.dto.ApiResponse;
import com.smartlms.dto.LessonRequest;
import com.smartlms.dto.LessonResponse;
import com.smartlms.service.LessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @GetMapping("/api/courses/{courseId}/lessons")
    public ResponseEntity<ApiResponse<List<LessonResponse>>> getLessonsByCourseId(@PathVariable Long courseId) {
        List<LessonResponse> lessons = lessonService.getLessonsByCourseId(courseId);
        return ResponseEntity.ok(ApiResponse.success("Lessons retrieved successfully", lessons));
    }

    @GetMapping("/api/lessons/{id}")
    public ResponseEntity<ApiResponse<LessonResponse>> getLessonById(@PathVariable Long id) {
        LessonResponse lesson = lessonService.getLessonById(id);
        return ResponseEntity.ok(ApiResponse.success("Lesson retrieved successfully", lesson));
    }

    @PostMapping("/api/courses/{courseId}/lessons")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @PathVariable Long courseId,
            @Valid @RequestBody LessonRequest request,
            Authentication authentication) {
        LessonResponse lesson = lessonService.createLesson(courseId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lesson created successfully", lesson));
    }

    @PutMapping("/api/lessons/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LessonResponse>> updateLesson(
            @PathVariable Long id,
            @Valid @RequestBody LessonRequest request,
            Authentication authentication) {
        LessonResponse lesson = lessonService.updateLesson(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lesson updated successfully", lesson));
    }

    @DeleteMapping("/api/lessons/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(
            @PathVariable Long id,
            Authentication authentication) {
        lessonService.deleteLesson(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lesson deleted successfully"));
    }
}
