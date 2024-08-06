package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
//	aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
		targetTable = "Events",
		batchSize = 10
)
@DependsOn(
		resourceType = ResourceType.DYNAMODB_TABLE,
		name = "Events"
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final ObjectMapper mapper = new ObjectMapper();
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		try {
			String id = UUID.randomUUID().toString();
			int principalId = (int) request.get("principalId");
			Map<String, String> content = (Map<String, String>) request.get("content");

			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", new AttributeValue().withS(id));
			item.put("principalId", new AttributeValue().withN(String.valueOf(principalId)));
			item.put("createdAt", new AttributeValue().withS(String.valueOf(System.currentTimeMillis())));
			item.put("body", new AttributeValue().withM(convertContentMap(content)));

			PutItemRequest putItemRequest = new PutItemRequest()
					.withTableName("Events")
					.withItem(item);
			client.putItem(putItemRequest);

			Map<String, Object> response = new HashMap<>();
			response.put("statusCode", 201);
			response.put("event", item);

			return response;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private Map<String, AttributeValue> convertContentMap(Map<String, String> content) {
		Map<String, AttributeValue> contentMap = new HashMap<>();
		for (Map.Entry<String, String> entry : content.entrySet()) {
			contentMap.put(entry.getKey(), new AttributeValue(entry.getValue()));
		}
		return contentMap;
	}
}
