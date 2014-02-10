package org.jojen.wikistudy.controller;


import org.jojen.wikistudy.service.*;
import org.jojen.wikistudy.util.RepositoryRefresher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


@Controller
@RequestMapping("/admin")
public class AdminController {

	@Inject
	protected CourseService courseService;

	@Inject
	protected LessonService lessonService;

	@Inject
	protected ContentService contentService;

	@Inject
	protected BlobService blobService;

	@Inject
	protected BackupService backupService;

	@RequestMapping(value = "/refresh", method = RequestMethod.GET)
	public String refresh() {
		RepositoryRefresher.refresh(courseService, lessonService, contentService);
		return "redirect:/";
	}

	@RequestMapping(value = "/shutdown", method = RequestMethod.GET)
	public String shutdown() {
		Runtime runtime = Runtime.getRuntime();
		try {
			// OS abhängig
			// Works only on Raspian (Alias)
			Process proc = runtime.exec("halt");
		} catch (IOException e) {

		}

		return "redirect:/";
	}
	@RequestMapping(value = "/settings", method = RequestMethod.GET)
	public String getSettings(Model model) {
		// OS abhängig
		File rootfile = new File("/");
		model.addAttribute("rootfile",rootfile);
		model.addAttribute("usablespace",blobService.readableFileSize(rootfile.getUsableSpace()));
		return "static/settings";
	}
	@RequestMapping(value = "/backup.zip", method = RequestMethod.GET)
	public void getAllBlobsZip(HttpServletResponse response) {
		File f = null;
		try {
			f = backupService.getBackup();
			response.setContentType("application/zip");
			response.setContentLength((int) (f.length() + 0));

			FileCopyUtils.copy(new FileInputStream(f), response.getOutputStream());
		} catch (Exception e) {
		   // dann gehts nicht
		} finally {
			if (f != null) {
				f.delete();
			}
		}

	}


}
