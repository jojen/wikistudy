package org.jojen.wikistudy.controller;

import org.jojen.wikistudy.domain.Actor;
import org.jojen.wikistudy.domain.Course;
import org.jojen.wikistudy.domain.Rating;
import org.jojen.wikistudy.domain.User;
import org.jojen.wikistudy.repository.UserRepository;
import org.jojen.wikistudy.repository.CourseRepository;
import org.jojen.wikistudy.repository.ActorRepository;
import org.jojen.wikistudy.service.DatabasePopulator;
import org.neo4j.helpers.collection.IteratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.neo4j.template.Neo4jOperations;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;

/**
 * @author mh
 * @since 04.03.11
 */
@Controller
public class CourseController {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ActorRepository actorRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    Neo4jOperations template;
    @Autowired
    private DatabasePopulator populator;
    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    /**
     * Only matches 'GET /moviies/{id}}' requests for JSON content; a 404 is sent otherwise.
     * TODO send a 406 if an unsupported representation, such as XML, is requested.  See SPR-7353.
     */
    @RequestMapping(value = "/movies/{id}", method = RequestMethod.GET, headers = "Accept=application/json")
    public
    @ResponseBody
	Course getMovie(@PathVariable String id) {
        return courseRepository.findById(id);
    }


    @RequestMapping(value = "/movies/{movieId}", method = RequestMethod.GET, headers = "Accept=text/html")
    public String singleMovieView(final Model model, @PathVariable String movieId) {
        User user = addUser(model);
        Course course = courseRepository.findById(movieId);
        model.addAttribute("id", movieId);
        if (course != null) {
            model.addAttribute("movie", course);
            final int stars = course.getStars();
            model.addAttribute("stars", stars);
            Rating rating = null;
            if (user!=null) rating = template.getRelationshipBetween(course, user, Rating.class, "RATED");
            if (rating == null) rating = new Rating().rate(stars,null);
            model.addAttribute("userRating",rating);
        }
        return "/movies/show";
    }

    @RequestMapping(value = "/movies/{movieId}", method = RequestMethod.POST, headers = "Accept=text/html")
    public String updateMovie(Model model, @PathVariable String movieId, @RequestParam(value = "rated",required = false) Integer stars, @RequestParam(value = "comment",required = false) String comment) {
        Course course = courseRepository.findById(movieId);
        User user = userRepository.getUserFromSession();
        if (user != null && course != null) {
            int stars1 = stars==null ? -1 : stars;
            String comment1 = comment!=null ? comment.trim() : null;
            userRepository.rate(course, user, stars1, comment1);
        }
        return singleMovieView(model,movieId);
    }

    private User addUser(Model model) {
        User user = userRepository.getUserFromSession();
        model.addAttribute("user", user);
        return user;
    }

    @RequestMapping(value = "/movies", method = RequestMethod.GET, headers = "Accept=text/html")
    public String findMovies(Model model, @RequestParam("q") String query) {
        if (query!=null && !query.isEmpty()) {
            Page<Course> movies = courseRepository.findByTitleLike(query, new PageRequest(0, 20));
            model.addAttribute("movies", movies.getContent());
        } else {
            model.addAttribute("movies", Collections.emptyList());
        }
        model.addAttribute("query", query);
        addUser(model);
        return "/movies/list";
    }

    @RequestMapping(value = "/actors/{id}", method = RequestMethod.GET, headers = "Accept=text/html")
    public String singleActorView(Model model, @PathVariable String id) {
        Actor actor = actorRepository.findById(id);
        model.addAttribute("actor", actor);
        model.addAttribute("id", id);
        model.addAttribute("roles",  IteratorUtil.asCollection(actor.getRoles()));
        addUser(model);
        return "/actors/show";
    }

    //@RequestMapping(value = "/admin/populate", method = RequestMethod.GET)
    @RequestMapping(value = "/populate", method = RequestMethod.GET)
    public String populateDatabase(Model model) {
        Collection<Course> courses=populator.populateDatabase();
        model.addAttribute("courses",courses);
        addUser(model);
        return "index";
    }

    @RequestMapping(value = "/admin/clean", method = RequestMethod.GET)
    public String clean(Model model) {
        populator.cleanDb();
        return "movies/list";
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index(Model model) {
		model.addAttribute("courses",courseRepository.findAll().iterator());
        addUser(model);
        return "index";
    }

	@RequestMapping(value = "/course/edit", method = RequestMethod.GET)
	public String getEdit(Model model,
							   @RequestParam(value = "id",required = true) String id
							   ) {
		addUser(model);
		model.addAttribute("self",courseRepository.findById(id));

		return "course/course.edit";
	}

    @RequestMapping(value = "/course/update", method = RequestMethod.GET)
    public String updateCourse(Model model,
                               @RequestParam(value = "id",required = false) String id,
                               @RequestParam(value = "key",required = true) String key,
                               @RequestParam(value = "value",required = true) String value) {
        Course c;
        if(id != null){
            c = courseRepository.findById(id);

        } else{
            c = new Course();
        }

        // TODO das ist nur zum testen
        if(key.equals("description")){
            c.setDescription(value);
        }else if(key.equals("title")){
            c.setTitle(value);
        }
        courseRepository.save(c);

        model.addAttribute("self",c.getId());
        addUser(model);

        return "json/id";
    }
}
