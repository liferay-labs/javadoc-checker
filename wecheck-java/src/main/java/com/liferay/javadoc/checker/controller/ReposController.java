/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.javadoc.checker.controller;

import com.liferay.javadoc.checker.model.Build;
import com.liferay.javadoc.checker.processor.BadgeManager;
import com.liferay.javadoc.checker.processor.BuildManager;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.logging.Logger;

@Controller
@RequestMapping(ReposController.REPO_ROOT_PATH)
public class ReposController {

	public static final String REPO_ROOT_PATH = "/repo";

	@GetMapping("/test")
	@ResponseBody
	public String test() {
		return "hello";
	}

	@GetMapping("/{repoOwner}/{repoName}/badge")
	public String getBadge(
			@PathVariable String repoOwner,
			@PathVariable String repoName,
			@RequestParam(required = false, defaultValue = "master")
				String branch)

		throws IOException, JSONException {

		double score = _buildManager.getScore(repoOwner, repoName, branch);;

		String redirectUrl = _badgeManager.getBadgeURL(score);

		return "redirect:" + redirectUrl;
  	}

	@GetMapping("/{repoOwner}/{repoName}/score")
	@ResponseBody
	public double getScore(
		@PathVariable String repoOwner,
		@PathVariable String repoName,
		@RequestParam(required = false, defaultValue = "master")
			String branch) {

		return _buildManager.getScore(repoOwner, repoName, branch);
	}

	@GetMapping("/{repoOwner}/{repoName}/build/latest")
	@ResponseBody
	public Build getLatestBuild(
		@PathVariable String repoOwner,
		@PathVariable String repoName,
		@RequestParam(required = false, defaultValue = "master")
			String branch) {

		return _buildManager.getBuild(repoOwner, repoName, branch);
	}

	@GetMapping("/{repoOwner}/{repoName}/build/{id}")
	@ResponseBody
	public Build getBuild(@PathVariable String id) {

		return _buildManager.getBuild(id);
	}

	@Autowired
	private BadgeManager _badgeManager;

	@Autowired
	private BuildManager _buildManager;

	private static final Logger LOGGER = Logger.getLogger(
		ReposController.class.getName());

}