package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@DependsOn(name = "Configuration", resourceType = ResourceType.DYNAMODB_TABLE)
@DependsOn(name = "Audit", resourceType = ResourceType.DYNAMODB_TABLE)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 10
)
@EnvironmentVariables(
		value = {
				@EnvironmentVariable(key = "region", value = "${region}"),
				@EnvironmentVariable(key = "target_table", value = "${target_table}"),
				@EnvironmentVariable(key = "config_table", value = "${config_table}")
		}
)
public class AuditProducer implements RequestHandler<Object, Map<String, Object>> {

	public Map<String, Object> handleRequest(Object request, Context context) {
		LambdaLogger logger = context.getLogger();

		logger.log("Request: " + request);
		logger.log("Region: " + System.getenv("region"));
		logger.log("Target table: " + System.getenv("target_table"));
		logger.log("Config table: " + System.getenv("config_table"));


		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap.put("statusCode", 200);
		resultMap.put("body", "Hello from Lambda");
		return resultMap;
	}
}

/*
Request: {Records=[{eventID=94376517d82336df6cfc03c3d767da81, eventName=INSERT, eventVersion=1.1, eventSource=aws:dynamodb, awsRegion=eu-central-1, dynamodb={ApproximateCreationDateTime=1.723369798E9, Keys={key={S=CACHE_TTL_SEC}}, NewImage={value={N=3600}, key={S=CACHE_TTL_SEC}}, SequenceNumber=2500000000069903933327, SizeBytes=39, StreamViewType=NEW_AND_OLD_IMAGES}, eventSourceARN=arn:aws:dynamodb:eu-central-1:196241772369:table/cmtr-43e49753-Configuration-test/stream/2024-08-11T09:47:48.547}]}
*/