package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
//	aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
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
	private boolean tableExists = false;
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final ObjectMapper mapper = new ObjectMapper();
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		LambdaLogger logger = context.getLogger();
		logger.log("Received request: " + request.toString());
		Map<String, Object> response = new HashMap<>();


		String createdAt = Instant.now().toString();

		try {
			String id = UUID.randomUUID().toString();
			int principalId = (int) request.get("principalId");
			Map<String, String> content = (Map<String, String>) request.get("content");

			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", new AttributeValue().withS(id));
			item.put("principalId", new AttributeValue().withN(String.valueOf(principalId)));
			item.put("createdAt", new AttributeValue().withS(createdAt));
			item.put("body", new AttributeValue().withM(convertContentMap(content)));

			PutItemRequest putItemRequest = new PutItemRequest()
					.withTableName("Events")
					.withItem(item);
			client.putItem(putItemRequest);

			logger.log("Successfully put item");

			Map<String, Object> eventResponse = new HashMap<>();
			eventResponse.put("id", id);
			eventResponse.put("principalId", principalId);
			eventResponse.put("createdAt", createdAt);
			eventResponse.put("body", content);

			response.put("statusCode", 201);
			response.put("event", eventResponse);

			return response;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			response.put("statusCode", 500);
			context.getLogger().log("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private boolean doesTableExist(String tableName) {
		try {
			DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
			client.describeTable(request);
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	private void createTable() {
		CreateTableRequest request = new CreateTableRequest()
				.withTableName("Events")
				.withKeySchema(new KeySchemaElement("id", KeyType.HASH))
				.withAttributeDefinitions(new AttributeDefinition("id", ScalarAttributeType.S))
				.withProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

		client.createTable(request);
		this.tableExists = true;
	}

	private Map<String, AttributeValue> convertContentMap(Map<String, String> content) {
		Map<String, AttributeValue> contentMap = new HashMap<>();
		for (Map.Entry<String, String> entry : content.entrySet()) {
			contentMap.put(entry.getKey(), new AttributeValue(entry.getValue()));
		}
		return contentMap;
	}
}
