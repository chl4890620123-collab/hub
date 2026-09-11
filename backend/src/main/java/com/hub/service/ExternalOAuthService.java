package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalOAuthService {
    private final HubProperties props;
    private final ExternalOAuthTokenStore store;
    private final RestClient client;
    private final ObjectMapper json;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    public ExternalOAuthService(HubProperties props, ExternalOAuthTokenStore store, RestClient.Builder builder, ObjectMapper json) {
        this.props=props;this.store=store;this.json=json;this.client=builder.requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5),Duration.ofSeconds(30))).build();
    }
    public String authorize(long userId,long projectId,String type,String redirectUri){
        String id=clientId(type),secret=clientSecret(type);if(blank(id)||blank(secret))throw new IllegalStateException(type+" OAuth 설정이 필요합니다.");
        String state=UUID.randomUUID().toString();pending.put(state,new Pending(userId,projectId,type,redirectUri,Instant.now().plusSeconds(600)));
        String base=switch(type){case "GITHUB"->"https://github.com/login/oauth/authorize";case "SLACK"->"https://slack.com/oauth/v2/authorize";default->"https://api.notion.com/v1/oauth/authorize";};
        String scope=switch(type){case "GITHUB"->"repo read:user";case "SLACK"->"channels:history,channels:read,users:read";default->"";};
        return UriComponentsBuilder.fromUriString(base).queryParam("client_id",id).queryParam("redirect_uri",redirectUri).queryParam("response_type","code").queryParam("state",state).queryParam("scope",scope).build().encode().toUriString();
    }
    public Pending callback(String code,String state){
        Pending p=pending.remove(state);if(p==null||p.expiresAt().isBefore(Instant.now()))throw new IllegalArgumentException("OAuth 요청이 만료되었습니다.");
        try{var form=new LinkedMultiValueMap<String,String>();form.add("client_id",clientId(p.type()));form.add("client_secret",clientSecret(p.type()));form.add("code",code);form.add("redirect_uri",p.redirectUri());
            String uri=switch(p.type()){case "GITHUB"->"https://github.com/login/oauth/access_token";case "SLACK"->"https://slack.com/api/oauth.v2.access";default->"https://api.notion.com/v1/oauth/token";};
            var request=client.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON);
            if("NOTION".equals(p.type())) request=request.header(HttpHeaders.AUTHORIZATION,"Basic "+java.util.Base64.getEncoder().encodeToString((clientId(p.type())+":"+clientSecret(p.type())).getBytes(StandardCharsets.UTF_8)));
            String body=request.body(form).retrieve().body(String.class);JsonNode n=json.readTree(body==null?"{}":body);String token=n.path("access_token").asText("");if(token.isBlank())throw new IllegalStateException("OAuth 토큰을 발급받지 못했습니다.");store.put(p.userId(),p.type(),token);return p;
        }catch(Exception e){throw new IllegalStateException("OAuth 연결에 실패했습니다.",e);}
    }
    public String token(long userId,String type){return store.get(userId,type);}
    public boolean connected(long userId,String type){return store.connected(userId,type);}
    private String clientId(String type){return switch(type){case "GITHUB"->props.githubClientId();case "SLACK"->props.slackClientId();default->props.notionClientId();};}
    private String clientSecret(String type){return switch(type){case "GITHUB"->props.githubClientSecret();case "SLACK"->props.slackClientSecret();default->props.notionClientSecret();};}
    private static boolean blank(String s){return s==null||s.isBlank();}
    public record Pending(long userId,long projectId,String type,String redirectUri,Instant expiresAt){}
}