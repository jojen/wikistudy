package org.jojen.wikistudy.service;

import org.jojen.wikistudy.entity.Course;
import org.jojen.wikistudy.entity.Lesson;
import org.springframework.data.domain.Page;

public interface LessonService {
	Page<Lesson> findAll(int page, int size);


	Lesson findById(Integer id);

	void add(Lesson lesson, Course c);

	Lesson update(Lesson lesson);

	void deleteById(Integer id);

	public Integer copy(Lesson l, Course c);

	void deleteAll();


}
