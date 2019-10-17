package com.daon.openam.onboarding;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.UUID;
import java.io.UnsupportedEncodingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.nashorn.internal.ir.ObjectNode;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.client.ClientConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by jbeloncik on 9/30/19.
 */
public class RestClient {
    private String REST_URI;
    private String AUTH;

    private final Logger logger = LoggerFactory.getLogger("amAuth");

    public RestClient(String restUri, String apiKey, String apiPassword) {
        REST_URI = restUri;

        try {
            String userPassStr = apiKey + ":" + apiPassword;
            AUTH = "Basic " + Base64.getEncoder().encodeToString(userPassStr.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("Error with Basic Auth encoding: " + e.getMessage());
        }
    }


    private Client client = ClientBuilder.newClient();

    public Response getUserDetails(String userName) {
        return client
                .target(REST_URI)
                .path("users")
                .queryParam("userId", userName)
                .queryParam("status", "ACTIVE")
                .queryParam("limit", 1)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", AUTH)
                .get(Response.class);
    }


    //get idCheck reviews
    //{{host}}/{{tenant}}/DigitalOnBoardingServices/rest/v1/users/{{user.id}}/idchecks/{{idcheck.id}}/reviews
    public Response getIdCheckReviews(String userId, String idCheckId) {

        String reviewPath = "users/" + userId + "/idchecks/" + idCheckId + "/reviews";

        return client
                .target(REST_URI)
                .path(reviewPath)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", AUTH)
                .get(Response.class);

    }

    public Response getIdCheckEvaluationResults(String userId, String idCheckId) {

        String evaluationPath = "users/" + userId + "/idchecks/" + idCheckId + "/evaluation";

        return client
                .target(REST_URI)
                .path(evaluationPath)
                .request(MediaType.APPLICATION_JSON)
                .header("Content-Type", "application/json")
                .header("Authorization", AUTH)
                .get(Response.class);
    }

}
