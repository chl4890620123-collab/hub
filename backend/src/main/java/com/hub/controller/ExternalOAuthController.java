package com.hub.controller;

import com.hub.model.User;
import com.hub.config.HubProperties;
import com.hub.service.CurrentUserService;
import com.hub.service.ExternalOAuthService;
import com.hub.service.ProjectAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class ExternalOAuthController {
    private final CurrentUserService current;private final ProjectAccessService access;private final ExternalOAuthService oauth;private final HubProperties props;
    public ExternalOAuthController(CurrentUserService current,ProjectAccessService access,ExternalOAuthService oauth,HubProperties props){this.current=current;this.access=access;this.oauth=oauth;this.props=props;}
    @GetMapping("/api/projects/{projectId}/connectors/{type}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable long projectId,@PathVariable String type,Authentication auth,HttpServletRequest request){User u=current.requireOperational(auth);access.requireAccess(projectId,u);String t=type.toUpperCase();String redirect=OAuthRedirects.callbackUri(props.publicBaseUrl(),request.getRequestURL().toString(),
                "/api/projects/"+projectId+"/connectors/"+type+"/authorize","/api/connectors/oauth/callback");
        try{return ResponseEntity.status(302).location(URI.create(oauth.authorize(u.id(),projectId,t,redirect))).build();}
        // Without CLIENT_ID/SECRET this is a setup gap, not an upstream failure: send the operator back with a message.
        catch(IllegalStateException notConfigured){return connectorRedirect(t,"failed","not_configured",null);}}
    /** Providers answer with ?code or ?error, so neither is required and a cancelled consent still lands on a readable page. */
    @GetMapping("/api/connectors/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required=false) String code,@RequestParam(required=false) String state,
                                         @RequestParam(required=false) String error){
        if(error!=null&&!error.isBlank())return connectorRedirect("EXTERNAL","failed",error,null);
        if(code==null||code.isBlank()||state==null||state.isBlank())return connectorRedirect("EXTERNAL","failed","missing_code",null);
        try{var p=oauth.callback(code,state);return connectorRedirect(p.type(),"connected",null,p.projectId());}
        catch(RuntimeException failure){
            String reason=failure.getMessage();
            if(reason==null||reason.isBlank()) reason="exchange_failed";
            return connectorRedirect("EXTERNAL","failed",reason,null);
        }
    }

    private ResponseEntity<Void> connectorRedirect(String type,String status,String reason,Long projectId){
        StringBuilder target=new StringBuilder("/?connector=").append(URLEncoder.encode(type,StandardCharsets.UTF_8)).append("&status=").append(status);
        if(reason!=null)target.append("&reason=").append(URLEncoder.encode(reason,StandardCharsets.UTF_8));
        if(projectId!=null)target.append("&project=").append(projectId);
        return ResponseEntity.status(302).location(URI.create(target.toString())).build();
    }
    @GetMapping("/api/projects/{projectId}/connectors/{type}/oauth-status")
    public Map<String,Object> status(@PathVariable long projectId,@PathVariable String type,Authentication auth){User u=current.requireOperational(auth);access.requireAccess(projectId,u);return Map.of("connected",oauth.connected(u.id(),type.toUpperCase()));}
}