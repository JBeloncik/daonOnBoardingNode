package com.daon.openam.onboarding;

import java.nio.charset.StandardCharsets;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by jbeloncik on 9/30/19.
 */
public class RestClient {
    private String restUri;
    private String auth;
    private Client client = ClientBuilder.newClient();


    private final Logger logger = LoggerFactory.getLogger("amAuth");

    RestClient(String restUri, String apiKey, String apiPassword) {
        this.restUri = restUri;

        String userPassStr = apiKey + ":" + apiPassword;
        auth = "Basic " + Base64.getEncoder().encodeToString(userPassStr.getBytes(StandardCharsets.UTF_8));
    }

    //TODO Not used
    public Response getUserDetails(String userName) {
        return client
                .target(restUri)
                .path("users")
                .queryParam("userId", userName)
                .queryParam("status", "ACTIVE")
                .queryParam("limit", 1)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", auth)
                .get(Response.class);
    }


    //get idCheck reviews
    //{{host}}/{{tenant}}/DigitalOnBoardingServices/rest/v1/users/{{user.id}}/idchecks/{{idcheck.id}}/reviews
    Response getIdCheckReviews(String userId, String idCheckId) {

        String reviewPath = "users/" + userId + "/idchecks/" + idCheckId + "/reviews";

        return client
                .target(restUri)
                .path(reviewPath)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", auth)
                .get(Response.class);

    }

    Response getIdCheckEvaluationResults(String userId, String idCheckId) {

        String evaluationPath = "users/" + userId + "/idchecks/" + idCheckId + "/evaluation";

        return client
                .target(restUri)
                .path(evaluationPath)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", auth)
                .get(Response.class);
    }

}
