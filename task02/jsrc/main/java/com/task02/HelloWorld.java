package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;

import java.util.LinkedHashMap;
import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE
)
public class HelloWorld implements RequestHandler<Map<String, Object>, String> {
	private final ObjectMapper mapper = new ObjectMapper();
	@Override
	public String handleRequest(Map<String, Object> request, Context context) {
        Map<String, Object> response = new LinkedHashMap<>();

		Map<String, Object> requestContext = (Map<String, Object>) request.get("requestContext");
		Map<String, Object> http = (Map<String, Object>) requestContext.get("http");

		String httpMethod = (String) http.get("method");
		String rawPath = (String) http.get("path");

		if (httpMethod.equals("GET") && rawPath.equalsIgnoreCase("/hello")) {
			response.put("statusCode", 200);
			response.put("message", "Hello from Lambda");
		} else {
			response.put("statusCode", 400);
			response.put(
					"message",
					String.format("Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", rawPath, httpMethod)
			);
		}

        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
