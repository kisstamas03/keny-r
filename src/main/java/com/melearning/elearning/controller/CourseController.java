package com.melearning.elearning.controller;

import com.melearning.elearning.model.Course;
import com.melearning.elearning.model.Presentation;
import com.melearning.elearning.model.User;
import com.melearning.elearning.service.CourseService;
import com.melearning.elearning.service.PresentationService;
import com.melearning.elearning.service.QuizService;
import com.melearning.elearning.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private PresentationService presentationService;

    @Autowired
    private UserService userService;

    @Autowired
    private QuizService quizService;

    @GetMapping
    public String listCourses(Model model, Authentication auth) {
        List<Course> courses;

        if (auth != null) {
            Optional<User> user = userService.getUserByUsername(auth.getName());
            if (user.isPresent()) {
                User currentUser = user.get();

                // Ha oktató, mutassa az összes kurzust
                if (currentUser.getRole().name().equals("INSTRUCTOR")) {
                    courses = courseService.getAllCourses();
                }
                // Ha diák, mutassa a publikus + beiratkozott privát kurzusokat
                else if (currentUser.getRole().name().equals("STUDENT")) {
                    List<Course> publicCourses = courseService.getPublicCourses();
                    List<Course> enrolledCourses = courseService.getEnrolledCourses(currentUser);

                    // Kombináljuk a kettőt (de ne duplikáljunk)
                    courses = new java.util.ArrayList<>(publicCourses);
                    for (Course enrolledCourse : enrolledCourses) {
                        if (!courses.contains(enrolledCourse)) {
                            courses.add(enrolledCourse);
                        }
                    }
                } else {
                    courses = courseService.getPublicCourses();
                }
            } else {
                courses = courseService.getPublicCourses();
            }
        } else {
            courses = courseService.getPublicCourses();
        }

        model.addAttribute("courses", courses);
        return "courses/list";
    }

    @GetMapping("/{id}")
    public String viewCourse(@PathVariable Long id, Model model, Authentication auth) {
        Optional<Course> course = courseService.getCourseById(id);

        if (course.isEmpty()) {
            return "redirect:/courses";
        }

        Course courseObj = course.get();

        // Ha a kurzus privát, ellenőrizzük a jogosultságot
        if (!courseObj.getIsPublic()) {
            if (auth == null) {
                return "redirect:/courses";
            }

            Optional<User> user = userService.getUserByUsername(auth.getName());
            if (user.isEmpty()) {
                return "redirect:/courses";
            }

            User currentUser = user.get();
            boolean isInstructor = courseObj.getInstructor().equals(currentUser);
            boolean isEnrolled = courseObj.getEnrolledUsers().contains(currentUser);

            // Ha sem oktató, sem beiratkozott, nem láthatja
            if (!isInstructor && !isEnrolled) {
                return "redirect:/courses";
            }
        }

        model.addAttribute("course", courseObj);
        model.addAttribute("presentations", presentationService.getPresentationsByCourse(courseObj));

        //teszt
        model.addAttribute("quizzes", quizService.getQuizzesByCourse(courseObj));

        if (auth != null) {
            Optional<User> user = userService.getUserByUsername(auth.getName());
            if (user.isPresent()) {
                boolean isEnrolled = courseObj.getEnrolledUsers().contains(user.get());
                model.addAttribute("isEnrolled", isEnrolled);
                model.addAttribute("user", user.get());
            }
        }

        if (!model.containsAttribute("isEnrolled")) {
            model.addAttribute("isEnrolled", false);
        }

        return "courses/view";
    }

    @GetMapping("/create")
    public String createCourseForm(Model model) {
        model.addAttribute("course", new Course());
        return "courses/create";
    }

    @PostMapping("/create")
    public String createCourse(@Valid @ModelAttribute("course") Course course,
                               Authentication auth,
                               @RequestParam(value = "presentations", required = false) MultipartFile[] presentations,
                               RedirectAttributes redirectAttributes) {

        Optional<User> instructor = userService.getUserByUsername(auth.getName());

        if (instructor.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Oktató nem található!");
            return "redirect:/courses/create";
        }

        try {
            course.setInstructor(instructor.get());
            Course savedCourse = courseService.saveCourse(course);

            // Prezentációk feltöltése és konvertálása
            if (presentations != null && presentations.length > 0) {
                int order = 1;
                for (MultipartFile file : presentations) {
                    if (!file.isEmpty()) {
                        String contentType = file.getContentType();
                        String fileName = file.getOriginalFilename();

                        // Ellenőrizzük, hogy megfelelő-e a kiterjesztés
                        if (fileName != null && (fileName.toLowerCase().endsWith(".pdf") ||
                                fileName.toLowerCase().endsWith(".ppt") ||
                                fileName.toLowerCase().endsWith(".pptx"))) {

                            // Itt hívjuk meg az új, konvertáló metódusunkat a Service-ből!
                            presentationService.processAndSavePresentation(file, savedCourse, order++);

                        } else {
                            redirectAttributes.addFlashAttribute("error", "Csak PDF, PPT vagy PPTX fájlokat lehet feltölteni!");
                            return "redirect:/courses/create";
                        }
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success", "Kurzus sikeresen létrehozva!");
            return "redirect:/courses";

        } catch (Exception e) {
            // Itt Exception-t kapunk el IOException helyett, mert a konvertálás dobhat más hibát is
            e.printStackTrace(); // Érdemes kinyomtatni a konzolra a hibát debuggoláshoz
            redirectAttributes.addFlashAttribute("error", "Hiba történt a fájl feldolgozása során: " + e.getMessage());
            return "redirect:/courses/create";
        }
    }

    @PostMapping("/{id}/enroll")
    public String enrollInCourse(@PathVariable Long id, Authentication auth) {
        Optional<Course> course = courseService.getCourseById(id);
        Optional<User> user = userService.getUserByUsername(auth.getName());

        if (course.isPresent() && user.isPresent()) {
            courseService.enrollUser(course.get(), user.get());
        }

        return "redirect:/courses/" + id;
    }
/*
    @GetMapping("/presentation/{id}")
    public ResponseEntity<byte[]> getPresentation(@PathVariable Long id) {
        Optional<Presentation> presentation = presentationService.getPresentationById(id);

        if (presentation.isPresent()) {
            Presentation p = presentation.get();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.inline()
                            .filename(p.getFileName())
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(p.getFileData());
        }



        return ResponseEntity.notFound().build();
    }*/
/*
    @GetMapping("/presentation/{id}/download")
    public ResponseEntity<byte[]> downloadPresentation(@PathVariable Long id) {
        Optional<Presentation> presentation = presentationService.getPresentationById(id);

        if (presentation.isPresent()) {
            Presentation p = presentation.get();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(p.getFileName())
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(p.getFileData());
        }

        return ResponseEntity.notFound().build();
    }*/

    @GetMapping("/{id}/manage")
    public String manageCourse(@PathVariable Long id, Model model, Authentication auth) {
        Optional<Course> course = courseService.getCourseById(id);

        if (course.isEmpty()) {
            return "redirect:/courses";
        }

        Course courseObj = course.get();

        // Csak az oktató láthatja aki létrehozta
        if (auth != null) {
            Optional<User> user = userService.getUserByUsername(auth.getName());
            if (user.isPresent() && courseObj.getInstructor().equals(user.get())) {
                model.addAttribute("course", courseObj);
                model.addAttribute("allStudents", userService.getAllUsers().stream()
                        .filter(u -> u.getRole().name().equals("STUDENT"))
                        .toList());
                return "courses/manage";
            }
        }

        return "redirect:/courses/" + id;
    }

    @PostMapping("/{id}/add-student")
    public String addStudent(@PathVariable Long id,
                             @RequestParam Long studentId,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        Optional<Course> course = courseService.getCourseById(id);
        Optional<User> student = userService.getUserById(studentId);

        if (course.isPresent() && student.isPresent()) {
            Course courseObj = course.get();

            // Csak az oktató adhat hozzá aki létrehozta
            if (auth != null) {
                Optional<User> user = userService.getUserByUsername(auth.getName());
                if (user.isPresent() && courseObj.getInstructor().equals(user.get())) {
                    courseService.enrollUser(courseObj, student.get());
                    redirectAttributes.addFlashAttribute("success", "Diák sikeresen hozzáadva!");
                }
            }
        }

        return "redirect:/courses/" + id + "/manage";
    }

    @PostMapping("/{id}/remove-student")
    public String removeStudent(@PathVariable Long id,
                                @RequestParam Long studentId,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        Optional<Course> course = courseService.getCourseById(id);
        Optional<User> student = userService.getUserById(studentId);

        if (course.isPresent() && student.isPresent()) {
            Course courseObj = course.get();

            // Csak az oktató távolíthat el aki létrehozta
            if (auth != null) {
                Optional<User> user = userService.getUserByUsername(auth.getName());
                if (user.isPresent() && courseObj.getInstructor().equals(user.get())) {
                    courseService.unenrollUser(courseObj, student.get());
                    redirectAttributes.addFlashAttribute("success", "Diák eltávolítva!");
                }
            }
        }

        return "redirect:/courses/" + id + "/manage";
    }

    @PostMapping("/{id}/add-presentations")
    public String addPresentationsToCourse(@PathVariable Long id,
                                           @RequestParam("newPresentations") MultipartFile[] newPresentations,
                                           Authentication auth,
                                           RedirectAttributes redirectAttributes) {
        Optional<Course> courseOpt = courseService.getCourseById(id);

        if (courseOpt.isPresent() && auth != null) {
            Course course = courseOpt.get();
            Optional<User> user = userService.getUserByUsername(auth.getName());

            // Biztonsági ellenőrzés: Csak a kurzus oktatója adhat hozzá új fájlt
            if (user.isPresent() && course.getInstructor().equals(user.get())) {

                if (newPresentations != null && newPresentations.length > 0) {
                    try {
                        // Megkeressük, hányadik fájlnál tartunk, hogy jó legyen a sorrend (opcionális)
                        int currentOrder = presentationService.getPresentationsByCourse(course).size() + 1;

                        for (MultipartFile file : newPresentations) {
                            if (!file.isEmpty()) {
                                String fileName = file.getOriginalFilename();
                                if (fileName != null && (fileName.toLowerCase().endsWith(".pdf") ||
                                        fileName.toLowerCase().endsWith(".ppt") ||
                                        fileName.toLowerCase().endsWith(".pptx"))) {

                                    // Ugyanazt a szerviz hívást használjuk, mint a létrehozásnál
                                    presentationService.processAndSavePresentation(file, course, currentOrder++);
                                }
                            }
                        }
                        redirectAttributes.addFlashAttribute("success", "Az új fájlok sikeresen hozzáadva!");
                    } catch (Exception e) {
                        redirectAttributes.addFlashAttribute("error", "Hiba a fájlok feltöltése közben: " + e.getMessage());
                    }
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Nincs jogosultságod módosítani ezt a kurzust!");
            }
        }

        // Visszairányítjuk a felhasználót a manage oldalra
        return "redirect:/courses/" + id + "/manage";
    }
}