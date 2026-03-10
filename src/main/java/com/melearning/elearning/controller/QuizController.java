package com.melearning.elearning.controller;

import com.melearning.elearning.model.Course;
import com.melearning.elearning.model.Quiz;
import com.melearning.elearning.model.User;
import com.melearning.elearning.service.CourseService;
import com.melearning.elearning.service.QuizService;
import com.melearning.elearning.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/courses/{courseId}/quizzes")
public class QuizController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private UserService userService;

    // Megjeleníti a kvíz-készítő űrlapot
    @GetMapping("/create")
    public String createQuizForm(@PathVariable Long courseId, Model model, Authentication auth) {
        Optional<Course> courseOpt = courseService.getCourseById(courseId);

        if (courseOpt.isEmpty()) {
            return "redirect:/courses";
        }

        // Itt is ellenőrizhetnéd, hogy az adott felhasználó-e az oktató, ahogy a CourseControllerben tetted!

        model.addAttribute("course", courseOpt.get());
        model.addAttribute("quiz", new Quiz()); // Egy üres kvízt küldünk a formnak

        return "courses/create-quiz"; // Ezt a HTML-t fogjuk mindjárt megírni
    }

    // Megjeleníti a tesztkitöltő oldalt a diáknak
    @GetMapping("/{quizId}/take")
    public String takeQuiz(@PathVariable Long courseId, @PathVariable Long quizId, Model model, Authentication auth) {
        Optional<Course> courseOpt = courseService.getCourseById(courseId);
        Optional<Quiz> quizOpt = quizService.getQuizById(quizId);

        if (courseOpt.isPresent() && quizOpt.isPresent()) {
            model.addAttribute("course", courseOpt.get());
            model.addAttribute("quiz", quizOpt.get());
            return "courses/take-quiz";
        }

        return "redirect:/courses";
    }

    // Fogadja a diák válaszait, kiértékeli, és megmutatja az eredményt
    @PostMapping("/{quizId}/submit")
    public String submitQuiz(@PathVariable Long courseId,
                             @PathVariable Long quizId,
                             @RequestParam java.util.Map<String, String> allParams,
                             Model model) {

        Optional<Quiz> quizOpt = quizService.getQuizById(quizId);
        Optional<Course> courseOpt = courseService.getCourseById(courseId);

        if (quizOpt.isPresent() && courseOpt.isPresent()) {
            Quiz quiz = quizOpt.get();
            int score = 0;
            int totalQuestions = quiz.getQuestions().size();

            // Végigmegyünk a kvíz összes kérdésén, és ellenőrizzük a beküldött válaszokat
            for (com.melearning.elearning.model.Question q : quiz.getQuestions()) {
                // A HTML formban a rádiógombok neve "question_1", "question_2", stb. (ahol a szám az ID)
                String submittedAnswer = allParams.get("question_" + q.getId());

                // Ha válaszolt, és a válasz megegyezik a helyes opcióval (A, B, C vagy D)
                if (submittedAnswer != null && submittedAnswer.equals(q.getCorrectOption())) {
                    score++;
                }
            }

            // Kiszámoljuk a százalékot
            int percentage = totalQuestions > 0 ? (int) Math.round(((double) score / totalQuestions) * 100) : 0;

            // Átadjuk az adatokat az eredmény-oldalnak
            model.addAttribute("course", courseOpt.get());
            model.addAttribute("quiz", quiz);
            model.addAttribute("score", score);
            model.addAttribute("totalQuestions", totalQuestions);
            model.addAttribute("percentage", percentage);

            return "courses/quiz-result";
        }

        return "redirect:/courses/" + courseId;
    }

    // Fogadja a kitöltött űrlapot és elmenti a kvízt a kérdésekkel együtt
    @PostMapping("/create")
    public String saveQuiz(@PathVariable Long courseId,
                           @ModelAttribute Quiz quiz,
                           Authentication auth,
                           RedirectAttributes redirectAttributes) {

        Optional<Course> courseOpt = courseService.getCourseById(courseId);

        if (courseOpt.isPresent()) {
            Course course = courseOpt.get();
            quiz.setCourse(course); // Hozzákötjük a kvízt a kurzushoz

            quizService.saveQuiz(quiz); // A Service elmenti a kvízt ÉS a benne lévő kérdéseket is

            redirectAttributes.addFlashAttribute("success", "Kérdéssor sikeresen hozzáadva a kurzushoz!");
            return "redirect:/courses/" + courseId + "/manage";
        }

        redirectAttributes.addFlashAttribute("error", "Hiba történt a kérdéssor mentésekor.");
        return "redirect:/courses";
    }
}