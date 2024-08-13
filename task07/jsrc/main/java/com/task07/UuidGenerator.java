package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.events.S3EventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(
		value = {
				@EnvironmentVariable(key = "region", value = "${region}"),
				@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
		}
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@DependsOn(
		name = "uuid_trigger",
		resourceType = ResourceType.CLOUDWATCH_RULE
)
@RuleEventSource(targetRule = "uuid_trigger")
public class UuidGenerator implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		LambdaLogger logger = context.getLogger();
		logger.log("Request: " + request);

		final String BUCKET_NAME = System.getenv("target_bucket");
		if (BUCKET_NAME == null || BUCKET_NAME.isEmpty()) {
			logger.log("Error: target_bucket environment variable is not set.");
			throw new RuntimeException("Environment variable target_bucket is required but not set.");
		}

		List<String> uuids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			uuids.add(UUID.randomUUID().toString());
		}
		logger.log("Generated UUIDs: " + uuids);

		String jsonOutput = "{ \"ids\": " + uuids + " }";
		String isoTime = Instant.now().toString();

		byte[] contentAsBytes = jsonOutput.getBytes(StandardCharsets.UTF_8);
		ByteArrayInputStream contentsAsStream = new ByteArrayInputStream(contentAsBytes);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentAsBytes.length);
		logger.log("Metadata Generated successfully");

		try {
			//checking if file already exists not to create duplicate
            if (s3Client.doesObjectExist(BUCKET_NAME, isoTime)) {
				logger.log("File with key " + isoTime + " already exists. Skipping creation.");
				return Map.of("status", "skipped", "message", "UUID file already exists");
			}
			s3Client.putObject(BUCKET_NAME, isoTime, contentsAsStream, metadata);
			context.getLogger().log("Successfully uploaded UUID file to S3 bucket: " + BUCKET_NAME);
		} catch (Exception e) {
			context.getLogger().log("Error uploading file to S3: " + e.getMessage());
		}

		return Map.of("status", "success", "message", "UUIDs generated and stored successfully");
	}
}
