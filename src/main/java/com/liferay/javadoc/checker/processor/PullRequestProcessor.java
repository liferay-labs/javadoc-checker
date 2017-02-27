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
package com.liferay.javadoc.checker.processor;

import static java.util.Collections.singleton;

import com.liferay.javadoc.checker.checkstyle.CheckStyleExecutor;
import com.liferay.javadoc.checker.configuration.JavadocCheckerConfigurationReader;
import com.liferay.javadoc.checker.github.GithubPullRequest;
import com.liferay.javadoc.checker.github.GithubPullRequestHead;
import com.liferay.javadoc.checker.github.GithubRepo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FileUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Julio Camarero
 */
public class PullRequestProcessor {

	public PullRequestProcessor(GithubPullRequest pullRequest) {
		_pullRequest = pullRequest;

		GithubPullRequestHead head = _pullRequest.getHead();

		_pullRequestRef = head.getRef();

		_pullRequestNumber = _pullRequest.getNumber();

		GithubRepo repo = head.getRepo();

		_repoFullName = repo.getFull_name();
	}

	public void process()
		throws GitAPIException, InterruptedException, IOException,
		JSONException, TransformerException {

		if (printInitialMessage) {
			JSONObject data = new JSONObject();

			data.put("body", "Checking JavaDocs...");

			tryToPostMessage(
				_MAX_RETRIES_DEFAULT, _repoFullName, _pullRequestNumber,
				data.toString());
		}

		String message = executeJavadocsChecker(_repoFullName, _pullRequestRef);

		tryToPostMessage(
			_MAX_RETRIES_DEFAULT, _repoFullName, _pullRequestNumber, message);
	}

	private String executeJavadocsChecker(String repoFullName, String ref)
		throws GitAPIException, InterruptedException, IOException,
		JSONException, TransformerException {

		Random random = new Random();

		String folderName = ref + String.valueOf(random.nextLong());
		String projectDir = "/tmp/" + folderName;
		File dir = new File(projectDir);

		LOGGER.info("Clonning git Repo.");

		String githubUser = System.getenv("githubUser");
		String githubKey = System.getenv("githubKey");

		Git git = Git.cloneRepository()
		.setURI("https://github.com/" +repoFullName)
		.setDirectory(dir)
		.setBranchesToClone(singleton("refs/heads/" + ref))
		.setBranch("refs/heads/" + ref)
		.setCredentialsProvider(
			new UsernamePasswordCredentialsProvider(githubUser, githubKey))
		.call();

		LOGGER.info("Executing checkStyle in repo.");

		JavadocCheckerConfigurationReader configurationReader =
			new JavadocCheckerConfigurationReader(projectDir);

		Map<String, Object> parameters = new HashMap();

		parameters.put("report-title", configurationReader.getReportTitle());

		JSONObject data = new JSONObject();

		CheckStyleExecutor checkStyleExecutor = new CheckStyleExecutor(
			configurationReader.getIncludeDirectories(),
			configurationReader.getExcludeDirectories(), parameters, true);

		String message = checkStyleExecutor.execute();

		data.put("body", message);

		FileUtils.deleteDirectory(dir);

		git.close();

		return data.toString();
	}

	private String postMessage(
			String repoFullName, String number, String message)
		throws IOException {

		StringBuilder url = new StringBuilder(5);

		url.append("https://api.github.com/repos/");
		url.append(repoFullName);
		url.append("/issues/");
		url.append(number);
		url.append("/comments");

		URL urlObject = new URL(url.toString());

		HttpURLConnection connection =
			(HttpURLConnection)urlObject.openConnection();

		connection.setRequestMethod("POST");

		String githubUser = System.getenv("githubUser");
		String githubKey = System.getenv("githubKey");

		String userpass = githubUser + ":" + githubKey;

		String basicAuth = "Basic " +
						DatatypeConverter.printBase64Binary(
							userpass.getBytes("UTF-8"));

		connection.setRequestProperty("Authorization", basicAuth);

		connection.setRequestProperty("Content-Type", "application/json");

		connection.setDoOutput(true);

		OutputStreamWriter out = new OutputStreamWriter(
			connection.getOutputStream());

		out.write(message);
		out.close();

		int bytes = 0;
		String line = null;

		StringBuilder sb = new StringBuilder();

		try (BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(connection.getInputStream()))) {

			while ((line = bufferedReader.readLine()) != null) {
				byte[] lineBytes = line.getBytes();

				bytes += lineBytes.length;

				if (bytes > (30 * 1024 * 1024)) {
					sb.append("Response for ");
					sb.append(url);
					sb.append(" was truncated due to its size.");

					break;
				}

				sb.append(line);
				sb.append("\n");
			}
		}

		String response = sb.toString();

		LOGGER.info("Comment posted successfully to Github");

		return response;
	}

	private void sleep(long duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}

	private void tryToPostMessage(
			int maxRetries, String repoFullName, String number, String message)
		throws IOException {

		int retryCount = 0;

		try {
			postMessage(repoFullName, number, message);
		}
		catch (IOException ioe) {
			LOGGER.warning("Error when posting comment in github.");

			retryCount++;

			if ((maxRetries >= 0) && (retryCount >= maxRetries)) {
				throw ioe;
			}

			LOGGER.warning(
				"Retrying in " + _RETRY_PERIOD_DEFAULT + " seconds. (" +
				retryCount + ")");

			sleep(1000 * _RETRY_PERIOD_DEFAULT);
		}
	}

	private static final Logger LOGGER = Logger.getLogger(
		PullRequestProcessor.class.getName());

	private final int _MAX_RETRIES_DEFAULT = 3;

	private final int _RETRY_PERIOD_DEFAULT = 5;

	private GithubPullRequest _pullRequest;
	private String _pullRequestNumber;
	private String _pullRequestRef;
	private String _repoFullName;
	private boolean printInitialMessage = false;

}