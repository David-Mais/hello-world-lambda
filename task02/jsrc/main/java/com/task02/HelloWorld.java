package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE
)
public class HelloWorld implements RequestHandler<Object, Map<String, Object>> {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Map<String, Object> handleRequest(Object request, Context context) {
		Map<String, Object> response = new HashMap<>();

		try {
			Map<String, Object> requestMap = objectMapper.convertValue(request, Map.class);

			String path = (String) requestMap.get("path");
			String method = (String) requestMap.get("httpMethod");

			if ("/hello".equals(path) && "GET".equalsIgnoreCase(method)) {
				response.put("statusCode", 200);
				response.put("message", "Hello from Lambda");
			} else {
				response.put("statusCode", 400);
				response.put("message", String.format("Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", path, method));
			}
		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("message", "Internal server error.");
		}

		return response;
	}
}
