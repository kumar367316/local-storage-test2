package com.custom.postprocessing.vault;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import com.microsoft.azure.keyvault.KeyVaultClient;

public class KeyVaultPropertyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	public static final String azureKeyVaultBaseUri = "azure.keyvault.uri";
	public static final String azureClientId = "azure.keyvault.client-id";
	public static final String azureClientKey = "azure.keyvault.client-key";

	@Override
	public void initialize(ConfigurableApplicationContext ctx) {
		ConfigurableEnvironment env = ctx.getEnvironment();

		String clientId = env.getProperty(azureClientId);
		String clientKey = env.getProperty(azureClientKey);
		String baseUri = env.getProperty(azureKeyVaultBaseUri);

		KeyVaultClient kvClient = new KeyVaultClient(new AzureKeyVaultCredential(clientId, clientKey));

		try {
			MutablePropertySources sources = env.getPropertySources();
			sources.addFirst(new KeyVaultPropertySource(new KeyVaultOperation(kvClient, baseUri)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}