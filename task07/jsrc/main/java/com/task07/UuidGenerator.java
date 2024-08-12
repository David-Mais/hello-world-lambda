package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Event;
import com.syndicate.deployment.annotations.EventSource;
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
import java.util.HashMap;
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
@DependsOn(
		name = "uuid_trigger",
		resourceType = ResourceType.CLOUDWATCH_RULE
)
@DependsOn(
		name = "uuid-storage",
		resourceType = ResourceType.S3_BUCKET
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@RuleEventSource(targetRule = "uuid_trigger")
@S3EventSource(targetBucket = "uuid-storage", events = {"s3:ObjectCreated:*"})
public class UuidGenerator implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();


	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		context.getLogger().log("Request: " + request);
		final String BUCKET_NAME = System.getenv("uuid-storage");

		List<String> uuids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			uuids.add(UUID.randomUUID().toString());
		}

		String jsonOutput = "{ \"ids\": " + uuids.toString() + " }";
		String isoTime = Instant.now().toString();

		byte[] contentAsBytes = jsonOutput.getBytes(StandardCharsets.UTF_8);
		ByteArrayInputStream contentsAsStream = new ByteArrayInputStream(contentAsBytes);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(contentAsBytes.length);

		try {
			s3Client.putObject(BUCKET_NAME, isoTime, contentsAsStream, metadata);
			context.getLogger().log("Successfully uploaded UUID file to S3 bucket: " + BUCKET_NAME);
		} catch (Exception e) {
			context.getLogger().log("Error uploading file to S3: " + e.getMessage());
		}

		Map<String, Object> response = new HashMap<>();
		response.put("status", "success");
		response.put("message", "UUIDs generated and stored successfully");
		response.put("uuids", uuids);

		return response;
	}
}
