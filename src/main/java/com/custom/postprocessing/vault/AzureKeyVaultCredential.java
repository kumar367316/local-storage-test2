package com.custom.postprocessing.vault;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.azure.keyvault.authentication.KeyVaultCredentials;

public class AzureKeyVaultCredential extends KeyVaultCredentials {
	private String appId;
	private String appSecret;

	public AzureKeyVaultCredential(String appId, String appSecret) {
		this.appId = appId;
		this.appSecret = appSecret;
	}

	@Override
	public String doAuthenticate(String authorization, String resource, String scope) {
		AuthenticationContext context = null;
		AuthenticationResult result = null;
		String token = "";

		try {
			context = new AuthenticationContext(authorization, false, Executors.newSingleThreadExecutor());

			ClientCredential credential = new ClientCredential(this.appId, this.appSecret);

			Future<AuthenticationResult> future = context.acquireToken(resource, credential, null);
			result = future.get(3, TimeUnit.MINUTES);
			token = result.getAccessToken();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return token;
	}
}