package org.jojen.wikistudy.controller;


import com.lowagie.text.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;

import com.lowagie.text.Paragraph;
import com.lowagie.text.html.simpleparser.HTMLWorker;
import com.lowagie.text.pdf.PdfWriter;
import org.jojen.wikistudy.entity.*;
import org.jojen.wikistudy.entity.Image;
import org.jojen.wikistudy.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/course")
public class CourseController {
	protected static final int DEFAULT_PAGE_NUM = 0;
	protected static final int DEFAULT_PAGE_SIZE = 5;

	@Inject
	protected CourseService courseService;

	@Inject
	protected LessonService lessonService;

	@Inject
	protected ContentService contentService;

	@Inject
	protected PDFService pdfService;


	protected static final Logger log = LoggerFactory
												.getLogger(CourseController.class);


	@RequestMapping(value = "/{courseId}/lesson/{id}", method = RequestMethod.GET)
	public String listLesson(
									@PathVariable("courseId") Integer cid,
									@PathVariable("id") Integer id,
									@RequestParam(value = "page", required = false) Integer page,
									Model model) {
		Course c = courseService.findById(cid);
		Lesson l = lessonService.findById(id);

		model.addAttribute("course", c);
		model.addAttribute("lesson", l);

		return "/course/course";
	}



	@RequestMapping(value = "/lesson/rename/{id}")
	public String renameLesson(@PathVariable("id") Integer id,
							   @RequestParam(value = "name") String name,
							   Model model) {
		Lesson l = lessonService.findById(id);
		l.setName(name);
		lessonService.update(l);
		return "/json/boolean";
	}


	@RequestMapping(value = "/{courseId}/lesson/delete/{id}")
	public String deleteLesson(
									  @PathVariable("courseId") Integer cid,
									  @PathVariable("id") Integer id,
									  Model model) {

		log.debug("delete id={}", id);
		Lesson l = lessonService.findById(id);

		Course c = courseService.findById(cid);
		if (!c.getLessons().isEmpty() && c.getLessons().size() > 1) {

			c.getLessons().remove(l.getPosition() - 1);

			lessonService.deleteById(id);
			courseService.update(c);

			int i = 1;
			for (Lesson lesson : c.getLessons()) {
				lesson.setPosition(i);
				lessonService.update(lesson);
				i++;
			}
		}
		lessonService.deleteById(id);


		return "/json/boolean";
	}


	@RequestMapping(value = "/{id}")
	public String show(
							  @PathVariable("id") Integer id,
							  @RequestParam(value = "page", required = false) Integer page,
							  Model model) {

		Integer lesson;
		Course c = courseService.findById(id);

		if (c.getLessons().isEmpty()) {
			Lesson l = new Lesson();
			lessonService.add(l, c);
			c.addLessons(l);
			courseService.update(c);
			lesson = l.getId();

		} else {
			lesson = c.getLessons().iterator().next().getId();
		}
		// TODO aktuelle lesson des benutzers

		return "redirect:/course/" + id + "/lesson/" + lesson;
	}

	@RequestMapping(value = "/{id}/add-lesson")
	public String addLesson(
								   @PathVariable("id") Integer id,
								   Model model) {
		Course c = courseService.findById(id);
		Lesson l = new Lesson();
		lessonService.add(l, c);
		c.addLessons(l);
		courseService.update(c);

		return "redirect:/course/" + id + "/lesson/" + l.getId();
	}


	@RequestMapping(value = "/edit", method = RequestMethod.GET)
	public String edit(@RequestParam(value = "id", required = false) Integer id, Model model) {
		if (id == null) {
			model.addAttribute(new Course());
		} else {
			model.addAttribute(courseService.findById(id));
		}

		return "/course/form";
	}

	@RequestMapping(value = "/move/content/{id}", method = RequestMethod.GET)
	public String moveContent(@PathVariable("id") Integer id,
							  @RequestParam(value = "from", required = true) Integer from,
							  @RequestParam(value = "to", required = true) Integer to,
							  Model model) {
		if (id != null && from != null && to != null) {
			Lesson l = lessonService.findById(id);
			List<Content> list = l.getContent();

			// Warum auch immer klappt das verschieben nur in eine Richtung
			if (from > to) {
				Collections.reverse(list);
				Collections.rotate(list.subList(list.size() - from - 1, list.size() - to), -1);
				Collections.reverse(list);
			} else {
				Collections.rotate(list.subList(from, to + 1), -1);
			}

			// Wir updaten noch alle positionen
			int i = 1;
			for (Content c : list) {
				c.setPosition(i);
				contentService.update(c);
				i++;
			}

			lessonService.update(l);

		}
		model.addAttribute("self", true);
		return "/json/boolean";
	}

	@RequestMapping(value = "/move/lesson/{id}", method = RequestMethod.GET)
	public String moveLesson(@PathVariable("id") Integer id,
							 @RequestParam(value = "from", required = true) Integer from,
							 @RequestParam(value = "to", required = true) Integer to,
							 Model model) {
		if (id != null && from != null && to != null) {
			Course c = courseService.findById(id);
			List<Lesson> list = c.getLessons();

			// Warum auch immer klappt das verschieben nur in eine Richtung
			if (from > to) {
				Collections.reverse(list);
				Collections.rotate(list.subList(list.size() - 1 - from, list.size() - to), -1);
				Collections.reverse(list);
			} else {
				Collections.rotate(list.subList(from, to + 1), -1);
			}


			// Wir updaten noch alle positionen

			int i = 1;
			for (Lesson l : list) {
				l.setPosition(i);
				lessonService.update(l);
				i++;
			}

			courseService.update(c);

		}
		model.addAttribute("self", true);
		return "/json/boolean";
	}

	@RequestMapping(value = "/form", method = RequestMethod.GET)
	public
	@ModelAttribute
	Course create(Model model) {
		Course course = new Course();
		return course;
	}

	@RequestMapping(value = "/form", method = RequestMethod.POST)
	public String createOnSubmit(@Valid Course course,
								 BindingResult bindingResult, Model model) {
		log.debug("create course={}", course);
		if (bindingResult.hasErrors()) {
			log.warn("validation error={}", bindingResult.getModel());
			model.addAllAttributes(bindingResult.getModel());
			return "/course/form";
		}
		courseService.insert(course);
		return "redirect:/";
	}


	@RequestMapping(value = "/edit", method = RequestMethod.POST)
	public String editOnSubmit(@Valid Course course,
							   BindingResult bindingResult,
							   @RequestParam(value = "id", required = false) Integer id
									  , Model model) {
		log.debug("edit course={}", course);
		if (bindingResult.hasErrors()) {
			log.warn("validation error={}", bindingResult.getModel());
			model.addAllAttributes(bindingResult.getModel());
			return "/course/form";
		}
		Course modelCourse;
		if (id == null) {
			modelCourse = new Course();
		} else {
			modelCourse = courseService.findById(id);
		}

		// TODO hier vielleicht noch ein bisschen reflections
		modelCourse.setName(course.getName());
		modelCourse.setDescription(course.getDescription());
		courseService.update(modelCourse);
		return "redirect:/";
	}

	@RequestMapping(value = "/delete/{id}")
	public String delete(
								@RequestParam(value = "page", required = false) Integer page,
								@PathVariable("id") Integer id,
								Model model) {
		log.debug("delete id={}", id);
		courseService.deleteById(id);
		model.addAttribute("self", true);
		return "/json/boolean";
	}

	@RequestMapping(value = "/pdf/{id}/{name}")
	public void pdf(@PathVariable("id") Integer id, HttpServletResponse response) {
		Course c = courseService.findById(id);

		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition","attachment");
		ByteArrayOutputStream stream = pdfService.getPdf(c);
		response.setContentLength(stream.size());
		try {
			stream.writeTo(response.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}


}
