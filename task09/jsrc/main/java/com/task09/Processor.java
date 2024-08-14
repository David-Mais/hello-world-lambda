package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = false,
	tracingMode = TracingMode.Active,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@DependsOn(
		name = "Weather",
		resourceType = ResourceType.DYNAMODB_TABLE
)
@EnvironmentVariables(
		value = {
				@EnvironmentVariable(key = "region", value = "${region}"),
				@EnvironmentVariable(key = "target_table", value = "${target_table}")
		}
)
public class Processor implements RequestHandler<Map<String, Object>, String> {

	public String handleRequest(Map<String, Object> request, Context context) {
		LambdaLogger logger = context.getLogger();
		logger.log(request.toString());

		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
		DynamoDB dynamoDB = new DynamoDB(client);
		Table table = dynamoDB.getTable(System.getenv("target_table"));

		try {
			String weatherDataJson = fetchWeatherData();
			logger.log("Weather Data Json: " + weatherDataJson);

			Map<String, Object> parsedData = parseWeatherData(weatherDataJson);
			logger.log("ParseData: " + parsedData);

			String id = UUID.randomUUID().toString();

			PutItemOutcome outcome = table.putItem(new Item().withPrimaryKey("id", id)
					.withMap("forecast", parsedData));
			logger.log("Outcome: " + outcome.toString());

			logger.log("Successfully stored weather data with ID: " + id);
			return "Success";
		} catch (Exception e) {
			logger.log("Error processing weather data: " + e.getMessage());
			return "Failed";
		}
	}

	private Map<String, Object> parseWeatherData(String weatherJson) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();

		// Parse the JSON string into a JsonNode object
		JsonNode rootNode = objectMapper.readTree(weatherJson);

		// Extract the required fields from the JSON
		Map<String, Object> forecast = new HashMap<>();
		forecast.put("elevation", rootNode.path("elevation").asDouble());
		forecast.put("generationtime_ms", rootNode.path("generationtime_ms").asDouble());

		Map<String, Object> hourly = new HashMap<>();
		hourly.put("temperature_2m", objectMapper.convertValue(rootNode.path("hourly").path("temperature_2m"), List.class));
		hourly.put("time", objectMapper.convertValue(rootNode.path("hourly").path("time"), List.class));

		forecast.put("hourly", hourly);

		Map<String, Object> hourlyUnits = new HashMap<>();
		hourlyUnits.put("temperature_2m", rootNode.path("hourly_units").path("temperature_2m").asText());
		hourlyUnits.put("time", rootNode.path("hourly_units").path("time").asText());

		forecast.put("hourly_units", hourlyUnits);
		forecast.put("latitude", rootNode.path("latitude").asDouble());
		forecast.put("longitude", rootNode.path("longitude").asDouble());
		forecast.put("timezone", rootNode.path("timezone").asText());
		forecast.put("timezone_abbreviation", rootNode.path("timezone_abbreviation").asText());
		forecast.put("utc_offset_seconds", rootNode.path("utc_offset_seconds").asInt());

		// Return the parsed data as a Map
		return forecast;
	}

	private String fetchWeatherData() throws Exception {
		String apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
		URL url = new URL(apiUrl);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");

		int responseCode = connection.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			// Read the response from the input stream
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuilder content = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}

			in.close();
			connection.disconnect();

			return content.toString();
		} else {
			throw new RuntimeException("Failed to fetch weather data: HTTP error code : " + responseCode);
		}
	}


}
