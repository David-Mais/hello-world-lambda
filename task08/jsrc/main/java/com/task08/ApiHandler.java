package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@LambdaLayer(
		layerName = "openmetroclient-layer",
		libraries = "lambda_layer/java/lib/task08-1.0.0.jar",
		runtime = DeploymentRuntime.JAVA11,
		architectures = Architecture.ARM64,
		artifactExtension = ArtifactExtension.JAR
)
public class ApiHandler implements RequestHandler<Map<String, Object>, String> {
	public String handleRequest(Map<String, Object> request, Context context) {
		LambdaLogger logger = context.getLogger();

		logger.log("Request: " + request.toString());
		OpenMetroClient client = new OpenMetroClient();
		try {
			String weatherForecast = client.getWeatherForecast();
			logger.log("Weather forecast: " + weatherForecast);
			return weatherForecast;
		} catch (Exception e) {
			logger.log("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
