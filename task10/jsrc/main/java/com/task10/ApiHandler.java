package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@DependsOn(name = "${tables_table}", resourceType = ResourceType.DYNAMODB_TABLE)
@DependsOn(name = "${reservations_table}", resourceType = ResourceType.DYNAMODB_TABLE)
@DependsOn(name = "${booking_userpool}", resourceType = ResourceType.COGNITO_USER_POOL)
@EnvironmentVariables(
		value = {
				@EnvironmentVariable(key = "region", value = "${region}"),
				@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
				@EnvironmentVariable(key = "reservations_table", value = "$reservations_table}"),
				@EnvironmentVariable(key = "booking_userpool", value = "${booking_userpool}")
		}
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private static final Log log = LogFactory.getLog(ApiHandler.class);
	private final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
	private final AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();

	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		LambdaLogger logger = context.getLogger();
		logger.log("Request: " + request.toString());

		logger.log("tables_table: " + System.getenv("tables_table"));
		logger.log("reservations_table: " + System.getenv("reservations_table"));
		logger.log("booking_userpool: " + System.getenv("booking_userpool"));

		Map<String, Object> responseMap = new HashMap<>();
		String path = (String) request.get("path");
		String httpMethod = (String) request.get("httpMethod");

		try {
			switch (path) {
				case "/signup":
					if ("POST".equalsIgnoreCase(httpMethod)) {
						responseMap = handleSignup(request);
					}
					break;
				case "/signin":
					if ("POST".equalsIgnoreCase(httpMethod)) {
						responseMap = handleSignin(request);
					}
					break;
				case "/tables":
					if ("GET".equalsIgnoreCase(httpMethod)) {
						responseMap = handleGetTables(request);
					} else if ("POST".equalsIgnoreCase(httpMethod)) {
						responseMap = handleCreateTable(request);
					}
					break;
				case "/reservations":
					if ("POST".equalsIgnoreCase(httpMethod)) {
						responseMap = handleCreateReservation(request);
					} else if ("GET".equalsIgnoreCase(httpMethod)) {
						responseMap = handleGetReservations(request);
					}
					break;
				default:
					responseMap.put("statusCode", 400);
					responseMap.put("body", "Invalid path.");
			}
		} catch (Exception e) {
			logger.log("Error: " + e.getMessage());
			responseMap.put("statusCode", 500);
			responseMap.put("body", "Internal server error.");
		}

		return responseMap;
	}

	private Map<String, Object> handleSignup(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();

		Map<String, String> body = (Map<String, String>) request.get("body");
		String firstName = body.get("firstName");
		String lastName = body.get("lastName");
		String email = body.get("email");
		String password = body.get("password");

		AdminCreateUserRequest cognitoRequest = new AdminCreateUserRequest()
				.withUserPoolId(System.getenv("COGNITO_USER_POOL_ID"))
				.withUsername(email)
				.withUserAttributes(
						new AttributeType().withName("given_name").withValue(firstName),
						new AttributeType().withName("family_name").withValue(lastName),
						new AttributeType().withName("email").withValue(email)
				)
				.withTemporaryPassword(password);

		cognitoClient.adminCreateUser(cognitoRequest);

		response.put("statusCode", 200);
		response.put("body", "Sign-up process is successful");
		return response;
	}

	private Map<String, Object> handleSignin(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();

		Map<String, String> body = (Map<String, String>) request.get("body");
		String email = body.get("email");
		String password = body.get("password");

		AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
				.withUserPoolId(System.getenv("COGNITO_USER_POOL_ID"))
				.withClientId(System.getenv("COGNITO_CLIENT_ID"))
				.withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
				.withAuthParameters(Map.of(
						"USERNAME", email,
						"PASSWORD", password
				));

		AdminInitiateAuthResult authResponse = cognitoClient.adminInitiateAuth(authRequest);
		String accessToken = authResponse.getAuthenticationResult().getAccessToken();

		response.put("statusCode", 200);
		response.put("body", Map.of("accessToken", accessToken));
		return response;
	}

	private Map<String, Object> handleGetTables(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();
		Table tablesTable = dynamoDB.getTable("Tables");

		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName("Tables"));
		response.put("statusCode", 200);
		response.put("body", scanResult.getItems());
		return response;
	}

	private Map<String, Object> handleCreateTable(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();

		Map<String, Object> body = (Map<String, Object>) request.get("body");

		Map<String, AttributeValue> item = new HashMap<>();
		for (Map.Entry<String, Object> entry : body.entrySet()) {
			item.put(entry.getKey(), convertToAttributeValue(entry.getValue()));
		}

		PutItemRequest putItemRequest = new PutItemRequest()
				.withTableName("Tables")
				.withItem(item);

		dynamoDBClient.putItem(putItemRequest);

		response.put("statusCode", 200);
		response.put("body", Map.of("id", body.get("id")));
		return response;
	}

	private AttributeValue convertToAttributeValue(Object value) {
		if (value instanceof String) {
			return new AttributeValue().withS((String) value);
		} else if (value instanceof Number) {
			return new AttributeValue().withN(value.toString());
		} else if (value instanceof Boolean) {
			return new AttributeValue().withBOOL((Boolean) value);
		}
		// Handle other types as needed
		throw new IllegalArgumentException("Unsupported attribute type: " + value.getClass().getSimpleName());
	}

	private Map<String, Object> handleCreateReservation(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();

		Map<String, Object> body = (Map<String, Object>) request.get("body");

		Map<String, AttributeValue> item = new HashMap<>();
		for (Map.Entry<String, Object> entry : body.entrySet()) {
			item.put(entry.getKey(), convertToAttributeValue(entry.getValue()));
		}

		PutItemRequest putItemRequest = new PutItemRequest()
				.withTableName("Reservations")
				.withItem(item);

		dynamoDBClient.putItem(putItemRequest);

		response.put("statusCode", 200);
		response.put("body", Map.of("reservationId", body.get("reservationId")));
		return response;
	}

	private Map<String, Object> handleGetReservations(Map<String, Object> request) {
		Map<String, Object> response = new HashMap<>();
		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName("Reservations"));

		response.put("statusCode", 200);
		response.put("body", scanResult.getItems());
		return response;
	}
}
