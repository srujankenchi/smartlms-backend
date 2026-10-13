package com.smartlms.config;

import com.smartlms.entity.Course;
import com.smartlms.entity.Lesson;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.repository.CourseRepository;
import com.smartlms.repository.LessonRepository;
import com.smartlms.repository.UserRepository;
import com.smartlms.util.YouTubeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Initializes and seeds the six required learning courses, YouTube lessons,
 * and default user accounts idempotently on application startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final List<CourseSeed> REQUIRED_COURSES = List.of(
            new CourseSeed(
                    "C-PROG",
                    "C Programming",
                    "Master the fundamentals of C Programming: syntax, pointers, memory management, and data structures.",
                    "Programming",
                    "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=600&auto=format&fit=crop&q=80",
                    "Complete C Programming Course",
                    "https://youtu.be/irqbmMNs2Bo"
            ),
            new CourseSeed(
                    "JAVA-PROG",
                    "Java Programming",
                    "Comprehensive guide to Object-Oriented Programming, Core Java, Collections, and best practices.",
                    "Programming",
                    "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&auto=format&fit=crop&q=80",
                    "Java Full Course",
                    "https://youtu.be/eIrMbAQSU34"
            ),
            new CourseSeed(
                    "PYTHON-PROG",
                    "Python Programming",
                    "Learn Python from scratch: data types, functions, modules, file I/O, and object-oriented concepts.",
                    "Programming",
                    "https://images.unsplash.com/photo-1526379095098-d400fd0bf935?w=600&auto=format&fit=crop&q=80",
                    "Python for Beginners",
                    "https://youtu.be/q3AuP01daL4"
            ),
            new CourseSeed(
                    "HTML-CSS",
                    "HTML & CSS",
                    "Build responsive, modern web pages using HTML5 semantic elements and modern CSS3 styling.",
                    "Web Development",
                    "https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=600&auto=format&fit=crop&q=80",
                    "HTML & CSS Full Course",
                    "https://youtu.be/qz0aGYrrlhU"
            ),
            new CourseSeed(
                    "DBMS",
                    "DBMS",
                    "Database Management Systems concepts: Relational model, SQL queries, normalization, and ACID properties.",
                    "Database",
                    "https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=600&auto=format&fit=crop&q=80",
                    "Database Management System Tutorial",
                    "https://youtu.be/hlGoQC332VM"
            ),
            new CourseSeed(
                    "REACT",
                    "React JS",
                    "Modern frontend development with React: components, hooks, state management, and routing.",
                    "Web Development",
                    "https://images.unsplash.com/photo-1633356122544-f134324a6cee?w=600&auto=format&fit=crop&q=80",
                    "React JS Full Course",
                    "https://youtu.be/CgkZ7MvWUAA"
            )
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Running SmartLMS DataInitializer...");

        // Ensure assignments table schema compatibility for max_marks and legacy due_date
        try {
            jdbcTemplate.execute("ALTER TABLE assignments ALTER COLUMN max_marks SET DEFAULT 100");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE assignments MODIFY COLUMN due_date DATETIME NULL");
        } catch (Exception ignored) {}

        // 0. Bootstrap initial administrator account if SMARTLMS_ADMIN_PASSWORD is configured
        String envAdminPassword = System.getenv("SMARTLMS_ADMIN_PASSWORD");
        if (envAdminPassword == null || envAdminPassword.isBlank()) {
            envAdminPassword = System.getProperty("smartlms.admin.password");
        }

        if (envAdminPassword != null && !envAdminPassword.isBlank()) {
            String adminEmail = System.getenv("SMARTLMS_ADMIN_EMAIL");
            if (adminEmail == null || adminEmail.isBlank()) {
                adminEmail = System.getProperty("smartlms.admin.email", "admin@smartlms.com");
            }
            final String finalAdminEmail = adminEmail.trim().toLowerCase();
            final String finalAdminPassword = envAdminPassword.trim();

            userRepository.findByEmail(finalAdminEmail)
                    .ifPresentOrElse(
                            existing -> log.info("Admin account '{}' already exists (role: {}). Skipping bootstrap.", finalAdminEmail, existing.getRole()),
                            () -> {
                                User adminUser = User.builder()
                                        .name("System Administrator")
                                        .email(finalAdminEmail)
                                        .password(passwordEncoder.encode(finalAdminPassword))
                                        .role(Role.ROLE_ADMIN)
                                        .enabled(true)
                                        .active(true)
                                        .build();
                                userRepository.save(adminUser);
                                log.info("Successfully bootstrapped initial administrator account: {}", finalAdminEmail);
                            }
                    );
        } else {
            log.info("SMARTLMS_ADMIN_PASSWORD not set. Initial admin bootstrap skipped.");
        }

        // 1. Ensure default teacher exists
        User teacher = userRepository.findByEmail("teacher@smartlms.com")
                .orElseGet(() -> {
                    User newTeacher = User.builder()
                            .name("Prof. Alex Turner")
                            .email("teacher@smartlms.com")
                            .password(passwordEncoder.encode("Password123!"))
                            .role(Role.ROLE_TEACHER)
                            .enabled(true)
                            .active(true)
                            .build();
                    log.info("Created default teacher: teacher@smartlms.com");
                    return userRepository.save(newTeacher);
                });

        // 2. Ensure default student exists
        userRepository.findByEmail("student@smartlms.com")
                .orElseGet(() -> {
                    User newStudent = User.builder()
                            .name("John Student")
                            .email("student@smartlms.com")
                            .password(passwordEncoder.encode("Password123!"))
                            .role(Role.ROLE_STUDENT)
                            .enabled(true)
                            .active(true)
                            .build();
                    log.info("Created default student: student@smartlms.com");
                    return userRepository.save(newStudent);
                });

        // 3. Hide any old sample courses not in the six required courses
        Set<String> requiredTitles = new HashSet<>();
        Set<String> requiredCodes = new HashSet<>();
        for (CourseSeed seed : REQUIRED_COURSES) {
            requiredTitles.add(seed.title().toLowerCase().trim());
            requiredCodes.add(seed.code().toLowerCase().trim());
        }

        List<Course> allCourses = courseRepository.findAll();
        for (Course existing : allCourses) {
            boolean isRequiredTitle = existing.getTitle() != null && requiredTitles.contains(existing.getTitle().toLowerCase().trim());
            boolean isRequiredCode = existing.getCode() != null && requiredCodes.contains(existing.getCode().toLowerCase().trim());
            if (!isRequiredTitle && !isRequiredCode) {
                if (existing.isPublished()) {
                    existing.setPublished(false);
                    courseRepository.save(existing);
                    log.info("Hidden old sample course: {} ({})", existing.getTitle(), existing.getCode());
                }
            }
        }

        // 4. Seed or update the six required courses and their YouTube lessons
        for (CourseSeed seed : REQUIRED_COURSES) {
            Optional<Course> courseByCode = courseRepository.findFirstByCodeIgnoreCase(seed.code());
            Optional<Course> courseByTitle = courseRepository.findFirstByTitleIgnoreCase(seed.title());

            Course course;
            if (courseByCode.isPresent()) {
                course = courseByCode.get();
                boolean courseUpdated = false;
                if (!seed.title().equalsIgnoreCase(course.getTitle())) {
                    course.setTitle(seed.title());
                    courseUpdated = true;
                }
                if (!course.isPublished()) {
                    course.setPublished(true);
                    courseUpdated = true;
                }
                if (!course.isActive()) {
                    course.setActive(true);
                    courseUpdated = true;
                }
                if (courseUpdated) {
                    course = courseRepository.save(course);
                }
            } else if (courseByTitle.isPresent()) {
                course = courseByTitle.get();
                boolean courseUpdated = false;
                if (!seed.code().equalsIgnoreCase(course.getCode())) {
                    course.setCode(seed.code());
                    courseUpdated = true;
                }
                if (!course.isPublished()) {
                    course.setPublished(true);
                    courseUpdated = true;
                }
                if (!course.isActive()) {
                    course.setActive(true);
                    courseUpdated = true;
                }
                if (courseUpdated) {
                    course = courseRepository.save(course);
                }
            } else {
                Course newCourse = Course.builder()
                        .code(seed.code())
                        .title(seed.title())
                        .description(seed.description())
                        .category(seed.category())
                        .thumbnailUrl(seed.thumbnailUrl())
                        .published(true)
                        .active(true)
                        .teacher(teacher)
                        .build();
                course = courseRepository.save(newCourse);
                log.info("Created course: {} ({})", seed.title(), seed.code());
            }

            // Ensure lesson exists for course
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderByOrderIndexAsc(course.getId());
            String embedUrl = YouTubeUtil.toEmbedUrl(seed.youtubeUrl());

            if (lessons.isEmpty()) {
                Lesson lesson = Lesson.builder()
                        .title(seed.lessonTitle())
                        .description(seed.description())
                        .content("Watch the embedded lecture video to complete this module.")
                        .videoUrl(embedUrl)
                        .orderIndex(1)
                        .active(true)
                        .course(course)
                        .build();
                lessonRepository.save(lesson);
                log.info("Created lesson for course {}: {}", course.getTitle(), lesson.getTitle());
            } else {
                // Update lesson videoUrl to ensure valid embed URL
                Lesson firstLesson = lessons.get(0);
                boolean lessonUpdated = false;
                if (!embedUrl.equals(firstLesson.getVideoUrl())) {
                    firstLesson.setVideoUrl(embedUrl);
                    lessonUpdated = true;
                }
                if (!firstLesson.isActive()) {
                    firstLesson.setActive(true);
                    lessonUpdated = true;
                }
                if (lessonUpdated) {
                    lessonRepository.save(firstLesson);
                }
            }
        }

        log.info("SmartLMS DataInitializer completed successfully.");
    }

    private record CourseSeed(
            String code,
            String title,
            String description,
            String category,
            String thumbnailUrl,
            String lessonTitle,
            String youtubeUrl
    ) {}
}
