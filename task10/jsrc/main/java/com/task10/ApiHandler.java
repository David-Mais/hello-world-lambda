package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserPasswordRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.ListUserPoolClientsRequest;
import com.amazonaws.services.cognitoidp.model.ListUserPoolClientsResult;
import com.amazonaws.services.cognitoidp.model.ListUserPoolsRequest;
import com.amazonaws.services.cognitoidp.model.ListUserPoolsResult;
import com.amazonaws.services.cognitoidp.model.MessageActionType;
import com.amazonaws.services.cognitoidp.model.UserPoolClientDescription;
import com.amazonaws.services.cognitoidp.model.UserPoolDescriptionType;
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
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
						responseMap = handleSignup(request, logger);
					}
					break;
				case "/signin":
					if ("POST".equalsIgnoreCase(httpMethod)) {
						responseMap = handleSignin(request, logger);
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

	private Map<String, Object> handleSignup(Map<String, Object> event, LambdaLogger logger) {
		Map<String, Object> response = new HashMap<>();
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			Map<String, Object> body = objectMapper.readValue((String) event.get("body"), Map.class);
			logger.log("signUp was called");

			String email = String.valueOf(body.get("email"));
			String password = String.valueOf(body.get("password"));

			if (!isEmailValid(email)) {
				logger.log("Email is invalid");
				throw new Exception("Email is invalid");
			}

			if (!isPasswordValid(password)) {
				logger.log("Password is invalid");
				throw new Exception("Password is invalid");
			}

			logger.log("Looking up user pool ID for: " + System.getenv("bookingUserPool"));
			String userPoolId = getUserPoolIdByName(System.getenv("bookingUserPool"))
					.orElseThrow(() -> new IllegalArgumentException("No such user pool"));
			logger.log("Found user pool ID: " + userPoolId);

			AdminCreateUserRequest adminCreateUserRequest = new AdminCreateUserRequest()
					.withUserPoolId(userPoolId)
					.withUsername(email)
					.withUserAttributes(new AttributeType().withName("email").withValue(email))
					.withMessageAction(MessageActionType.SUPPRESS);
			logger.log("AdminCreateUserRequest: " + adminCreateUserRequest.toString());

			AdminSetUserPasswordRequest adminSetUserPassword = new AdminSetUserPasswordRequest()
					.withPassword(password)
					.withUserPoolId(userPoolId)
					.withUsername(email)
					.withPermanent(true);
			logger.log(adminSetUserPassword.toString());

			logger.log("Creating user in Cognito...");
			cognitoClient.adminCreateUser(adminCreateUserRequest);
			logger.log("User created successfully.");

			logger.log("Setting user password...");
			cognitoClient.adminSetUserPassword(adminSetUserPassword);
			logger.log("Password set successfully.");

			response.put("statusCode", 200);
			response.put("body", "User created successfully");

		} catch (Exception ex) {
			logger.log(ex.toString());
			response.put("statusCode", 400);
			response.put("body", ex.getMessage());
		}
		return response;
	}


	private Map<String, Object> handleSignin(Map<String, Object> event, LambdaLogger logger) {
		logger.log("signIn was called");
		Map<String, Object> response = new HashMap<>();
		ObjectMapper objectMapper = new ObjectMapper();

		try {
			Map<String, Object> body = objectMapper.readValue((String) event.get("body"), Map.class);
			logger.log("signIn was called");

			String email = String.valueOf(body.get("email"));
			String password = String.valueOf(body.get("password"));

			if (!isEmailValid(email)) {
				logger.log("Email is invalid");
				throw new Exception("Email is invalid");
			}

			if (!isPasswordValid(password)) {
				logger.log("Password is invalid");
				throw new Exception("Password is invalid");
			}

			String userPoolId = getUserPoolIdByName(System.getenv("bookingUserPool"))
					.orElseThrow(() -> new IllegalArgumentException("No such user pool"));

			String clientId = getClientIdByUserPoolName(System.getenv("bookingUserPool"))
					.orElseThrow(() -> new IllegalArgumentException("No such client ID"));

			Map<String, String> authParams = new HashMap<>();
			authParams.put("USERNAME", email);
			authParams.put("PASSWORD", password);
			logger.log("AuthParams" + authParams);

			AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
					.withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
					.withUserPoolId(userPoolId)
					.withClientId(clientId)
					.withAuthParameters(authParams);
			logger.log(authRequest.toString());

			AdminInitiateAuthResult result = cognitoClient.adminInitiateAuth(authRequest);
			String accessToken = result.getAuthenticationResult().getIdToken();
			logger.log("AccessToken: " + accessToken);

			Map<String, Object> jsonResponse = new HashMap<>();

			response.put("statusCode", 200);
			response.put("body", objectMapper.writeValueAsString(jsonResponse));
			logger.log("Json Response: " + jsonResponse);
		} catch (Exception ex) {
			logger.log("Exception" + ex);
			response.put("statusCode", 400);
			response.put("body", ex.getMessage());
		}

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

	public static boolean isEmailValid(String email) {
		final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
		final Pattern pattern = Pattern.compile(EMAIL_PATTERN);
		if (email == null) {
			return false;
		}

		Matcher matcher = pattern.matcher(email);
		return matcher.matches();
	}

	public static boolean isPasswordValid(String password) {
		if (password == null) {
			return false;
		}

		return password.length() >= 8 &&
				password.length() <= 20 &&
				password.matches(".*[A-Z].*") &&
				password.matches(".*[a-z].*") &&
				password.matches(".*\\d.*") &&
				password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
	}

	public Optional<String> getUserPoolIdByName(String userPoolName) {
		String nextToken = null;

		do {
			ListUserPoolsRequest listUserPoolsRequest = new ListUserPoolsRequest()
					.withMaxResults(60)
					.withNextToken(nextToken);

			ListUserPoolsResult listUserPoolsResult = cognitoClient.listUserPools(listUserPoolsRequest);

			for (UserPoolDescriptionType pool : listUserPoolsResult.getUserPools()) {
				if (pool.getName().equals(userPoolName)) {
					return Optional.of(pool.getId());
				}
			}

			nextToken = listUserPoolsResult.getNextToken();
		} while (nextToken != null);

		return Optional.empty();
	}

	public Optional<String> getClientIdByUserPoolName(String userPoolName) {
		String userPoolId = getUserPoolIdByName(userPoolName).get();

		ListUserPoolClientsRequest listUserPoolClientsRequest = new ListUserPoolClientsRequest().withUserPoolId(userPoolId);
		ListUserPoolClientsResult listUserPoolClientsResult = cognitoClient.listUserPoolClients(listUserPoolClientsRequest);

		for (UserPoolClientDescription client : listUserPoolClientsResult.getUserPoolClients()) {
			return Optional.of(client.getClientId());
		}

		return Optional.empty();
	}
}
