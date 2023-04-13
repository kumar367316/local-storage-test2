package com.custom.postprocessing.vault;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.models.SecretBundle;
import com.microsoft.azure.keyvault.models.SecretItem;

public class KeyVaultOperation {
	private KeyVaultClient client;
	private String baseUri;
	private String[] propertyNames;

	public KeyVaultOperation(KeyVaultClient kvClient, String keyVaultBaseUri) {
		this.client = kvClient;
		this.baseUri = keyVaultBaseUri;
	}

	public String[] list() {
		if (baseUri.endsWith("/")) {
			baseUri = baseUri.substring(0, baseUri.length() - 1);
		}

		if (propertyNames == null) {
			try {
				PagedList<SecretItem> secrets = client.listSecrets(baseUri);
				List<String> values = secrets.stream().map(s -> s.id())
						.map(s -> s.replaceFirst(baseUri + "/secrets/", "")).collect(Collectors.toList());

				propertyNames = values.stream().toArray(String[]::new);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return propertyNames;
	}

	public Object get(String secretName) {
		// NOTE: azure keyvault secret name convention: [0-9a-zA-Z-]+$. "." is not
		// allowed
		secretName = secretName.replace(".", "-");
		try {
			if (propertyNames == null) {
				list();
			}

			if (Arrays.asList(propertyNames).contains(secretName)) {
				SecretBundle secret = client.getSecret(baseUri, secretName);
				try {
					int valueInt = Integer.parseInt((secret.value()));
					return valueInt;
				} catch (NumberFormatException ne) {
					return secret.value();
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}