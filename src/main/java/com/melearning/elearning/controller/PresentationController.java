package com.melearning.elearning.controller;

import com.melearning.elearning.model.Presentation;
import com.melearning.elearning.service.PresentationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ContentDisposition;

import java.util.Optional;

@Controller
public class PresentationController {

    @Autowired
    private PresentationService presentationService;

    @GetMapping("/presentations/{id}/view")
    public String viewPresentation(@PathVariable Long id, Model model) {
        Optional<Presentation> presentationOpt = presentationService.getPresentationById(id);

        if (presentationOpt.isEmpty()) {
            return "redirect:/courses";
        }

        Presentation presentation = presentationOpt.get();
        model.addAttribute("presentation", presentation);
        model.addAttribute("courseId", presentation.getCourse().getId());

        return "presentation/viewer";
    }

    @GetMapping("/presentations/{id}/content")
    public ResponseEntity<byte[]> getPresentationContent(@PathVariable Long id) {
        Optional<Presentation> presentationOpt = presentationService.getPresentationById(id);

        if (presentationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Presentation presentation = presentationOpt.get();
        String fileName = presentation.getFileName().toLowerCase();

        HttpHeaders headers = new HttpHeaders();

        // Ha PDF, akkor a böngészőben nyitjuk meg (inline)
        if (fileName.endsWith(".pdf")) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.inline().filename(presentation.getFileName()).build()
            );
        }
        // Bármi más esetén letöltés (attachment)
        else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(presentation.getFileName()).build()
            );
        }

        return new ResponseEntity<>(presentation.getFileData(), headers, HttpStatus.OK);
    }
}