package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.model.User;
import com.hub.repository.ProjectRepository;
import com.hub.repository.UserRepository;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalOAuthService {
    private static final Logger log = LoggerFactory.getLogger(ExternalOAuthService.class);
    private final HubProperties props;
    private final ExternalOAuthTokenStore store;
    private final RestClient client;
    private final ObjectMapper json;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    public ExternalOAuthService(HubProperties props, ExternalOAuthTokenStore store, RestClient.Builder builder, ObjectMapper json,
                                UserRepository users, ProjectRepository projects) {
        this.props=props;this.store=store;this.json=json;this.users=users;this.projects=projects;
        this.client=builder.requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5),Duration.ofSeconds(30))).build();
    }
    public String authorize(long userId,long projectId,String type,String redirectUri){
        String id=clientId(type),secret=clientSecret(type);if(blank(id)||blank(secret))throw new IllegalStateException(type+" OAuth 설정이 필요합니다.");
        String state=UUID.randomUUID().toString();pending.put(state,new Pending(userId,projectId,type,redirectUri,Instant.now().plusSeconds(600)));
        String base=switch(type){case "GITHUB"->"https://github.com/login/oauth/authorize";case "SLACK"->"https://slack.com/oauth/v2/authorize";default->"https://api.notion.com/v1/oauth/authorize";};
        String scope=switch(type){case "GITHUB"->"repo read:user";case "SLACK"->"channels:history,channels:read,users:read";default->"";};
        var url=UriComponentsBuilder.fromUriString(base).queryParam("client_id",id).queryParam("redirect_uri",redirectUri).queryParam("response_type","code").queryParam("state",state).queryParam("scope",scope);
        if ("SLACK".equals(type) && !blank(props.slackTeamId())) {
            url = url.queryParam("team", props.slackTeamId());
        }
        // Notion rejects an authorize call without it; the others take no equivalent switch.
        if("NOTION".equals(type))url=url.queryParam("owner","user");
        return url.build().encode().toUriString();
    }
    public Pending callback(String code,String state){
        Pending p=pending.remove(state);if(p==null||p.expiresAt().isBefore(Instant.now()))throw new IllegalArgumentException("OAuth 요청이 만료되었습니다.");
        User user=users.findById(p.userId()).filter(User::active)
                .orElseThrow(()->new IllegalStateException("계정 상태가 변경되어 연결을 완료할 수 없습니다."));
        if(!projects.canAccess(p.projectId(),user.id(),user.isAdmin()))
            throw new IllegalStateException("프로젝트 접근 권한이 변경되어 연결을 완료할 수 없습니다.");
        try{var form=new LinkedMultiValueMap<String,String>();form.add("client_id",clientId(p.type()));form.add("client_secret",clientSecret(p.type()));form.add("code",code);form.add("redirect_uri",p.redirectUri());
            String uri=switch(p.type()){case "GITHUB"->"https://github.com/login/oauth/access_token";case "SLACK"->"https://slack.com/api/oauth.v2.access";default->"https://api.notion.com/v1/oauth/token";};
            var request=client.post().uri(uri).accept(MediaType.APPLICATION_JSON);
            String body;
            if ("NOTION".equals(p.type())) {
                request=request.contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION,"Basic "+java.util.Base64.getEncoder().encodeToString(
                                (clientId(p.type())+":"+clientSecret(p.type())).getBytes(StandardCharsets.UTF_8)));
                body=request.body(Map.of(
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", p.redirectUri()
                )).retrieve().body(String.class);
            } else {
                body=request.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form).retrieve().body(String.class);
            }
            JsonNode n=json.readTree(body==null?"{}":body);
            String token=n.path("access_token").asText("");
            if(token.isBlank()) {
                String providerError=n.path("error_description").asText(n.path("error").asText(""));
                log.warn("OAuth token exchange returned no access token for {}: {}", p.type(),
                        providerError.isBlank() ? "empty provider response" : providerError);
                throw new IllegalStateException(providerError.isBlank()
                        ? "OAuth 토큰을 발급받지 못했습니다."
                        : "OAuth 토큰 교환 실패: " + providerError);
            }
            store.put(p.userId(),p.projectId(),p.type(),token,accountLabel(p.type(),n,token));return p;
        }catch(HttpStatusCodeException e){
            log.warn("OAuth token exchange rejected for {}: HTTP {} {}", p.type(), e.getStatusCode().value(), providerError(e.getResponseBodyAsString()));
            throw new IllegalStateException("OAuth 토큰 교환 HTTP " + e.getStatusCode().value()
                    + ": " + providerError(e.getResponseBodyAsString()), e);
        }catch(IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalStateException("OAuth 연결 저장에 실패했습니다.",e);}
    }
    public String token(long userId,String type){return store.get(userId,type);}
    public boolean connected(long userId,String type){return store.connected(userId,type);}
    public void disconnect(long userId,String type){store.remove(userId,type);}
    public String accountLabel(long userId,String type){return store.accountLabel(userId,type);}
    public Long accountId(long userId,String type){return store.accountId(userId,type);}
    private String clientId(String type){return switch(type){case "GITHUB"->props.githubClientId();case "SLACK"->props.slackClientId();default->props.notionClientId();};}
    private String clientSecret(String type){return switch(type){case "GITHUB"->props.githubClientSecret();case "SLACK"->props.slackClientSecret();default->props.notionClientSecret();};}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String providerError(String body) {
        if (body == null || body.isBlank()) return "제공자 응답이 비어 있습니다.";
        try {
            JsonNode n = new ObjectMapper().readTree(body);
            String detail = n.path("error_description").asText(n.path("error").asText(""));
            return detail.isBlank() ? "제공자가 요청을 거부했습니다." : detail;
        } catch (Exception ignored) {
            return "제공자가 요청을 거부했습니다.";
        }
    }
    public record Pending(long userId,long projectId,String type,String redirectUri,Instant expiresAt){}

    /** Names the external account that authorised this link, for the connector screen. */
    private String accountLabel(String type,JsonNode tokenResponse,String token){
        try{
            if("SLACK".equals(type)){
                String team=tokenResponse.path("team").path("name").asText("");
                String user=tokenResponse.path("authed_user").path("id").asText("");
                String label=team.isBlank()?user:team;
                return label.isBlank()?null:label;
            }
            if("NOTION".equals(type)){
                String workspace=tokenResponse.path("workspace_name").asText("");
                if(!workspace.isBlank())return workspace;
                String owner=tokenResponse.path("owner").path("user").path("name").asText("");
                return owner.isBlank()?null:owner;
            }
            if("GITHUB".equals(type)){
                String body=client.get().uri("https://api.github.com/user")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer "+token)
                        .header(HttpHeaders.ACCEPT,"application/vnd.github+json")
                        .retrieve().body(String.class);
                String login=json.readTree(body==null?"{}":body).path("login").asText("");
                return login.isBlank()?null:login;
            }
        }catch(Exception ignored){/* a missing label must not fail an otherwise good connection */}
        return null;
    }
}