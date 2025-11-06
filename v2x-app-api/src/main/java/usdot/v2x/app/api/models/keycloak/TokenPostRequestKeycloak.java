package usdot.v2x.app.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
public class TokenPostRequestKeycloak {
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("client_secret")
    private String clientSecret;
    @JsonProperty("grant_type")
    private String grantType;
    @JsonProperty("username")
    private String username;
    @JsonProperty("password")
    private String password;
    @JsonProperty("scope")
    private String scope;

    public TokenPostRequestKeycloak(TokenPostRequest request, String clientId, String clientSecret, String grantType,
            String scope) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = grantType;
        this.username = request.getUsername();
        this.password = request.getPassword();
        this.scope = scope;
    }

    public MultiValueMap<String, String> getFormData() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", grantType);
        formData.add("scope", scope);
        formData.add("username", username);
        formData.add("password", password);
        return formData;
    }
}
