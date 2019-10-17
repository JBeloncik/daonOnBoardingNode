/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package com.daon.openam.onboarding;

import java.util.*;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.identity.sm.RequiredValueValidator;
import java.io.IOException;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.annotations.adapters.Password;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.assistedinject.Assisted;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that username is in a group
 * permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider  = AbstractDecisionNode.OutcomeProvider.class,
               configClass      = daonOnBoardingNode.Config.class)
public class daonOnBoardingNode extends AbstractDecisionNode {

    private final Logger logger = LoggerFactory.getLogger("amAuth");
    private final Config config;
    private final Realm realm;

    /**
     * Configuration for the node.
     */
    public interface Config {
        /**
         * base URL for the IdentityX server
         * @return the URL
         */
        @Attribute(order = 100, validators = {RequiredValueValidator.class})
        String identityXBaseUrl();

        /**
         * the api key for API access
         * @return the api key
         */
        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        String apiKey();

        /**
         * password for the api key
         * @return the apiPassword
         */
        @Attribute(order = 300, validators = {RequiredValueValidator.class})
        @Password
        char[] apiPassword();

    }


    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of other classes
     * from the plugin.
     *
     * @param config The service config.
     * @param realm The realm the node is in.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public daonOnBoardingNode(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {
        this.config = config;
        this.realm = realm;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        List<TextInputCallback> textInputCallbackList = context.getCallbacks(TextInputCallback.class);

        if (context.hasCallbacks() && (textInputCallbackList.size()) > 0) {
            String idCheckIdVal = "";
            String userIdVal = "";

            for (TextInputCallback tic : textInputCallbackList) {
                if (tic.getDefaultText().equalsIgnoreCase("idcheck.id")) {
                    idCheckIdVal = tic.getText();
                } else if (tic.getDefaultText().equalsIgnoreCase("user.id")) {
                    userIdVal = tic.getText();
                }

            }

            String idCheckReviewDecision = getIdCheckReviewDecision(userIdVal, idCheckIdVal);

            if (idCheckReviewDecision.equalsIgnoreCase("REVIEW_APPROVED") ||
                    idCheckReviewDecision.equalsIgnoreCase("AUTO_APPROVED")) {
                return goTo(true).build();
            } else {
                return goTo(false).build();
            }
        }

        List<Callback> callbacks = new ArrayList<>();

        callbacks.add(new TextOutputCallback(TextOutputCallback.INFORMATION, "Please provide IdCheckId"));
        callbacks.add(new TextInputCallback("Please provide the IdCheckId", "idcheck.id"));
        callbacks.add(new TextOutputCallback(TextOutputCallback.INFORMATION, "Please provide UserId"));
        callbacks.add(new TextInputCallback("Please provide the userID", "user.id"));

        return Action.send(callbacks).build();
    }

    /**
     * This method gets the result value from the evaluation.
     *
     * Note that this will only be present if a rules set is applied in the
     * evaluationPolicy. For this reason, it may be better to just check the
     * decision value of the review.
     */
    private String getIdCheckResult(String userId, String idCheckId) {
        String idxBaseUrl = config.identityXBaseUrl();
        String apiKey = config.apiKey();
        String apiPassword = String.valueOf(config.apiPassword());

        RestClient client = new RestClient(idxBaseUrl, apiKey, apiPassword);

        // Get idCheck details
        Response response = client.getIdCheckEvaluationResults(userId, idCheckId);
        String jsonString = response.readEntity(String.class);

        //get the ID Check result from the evaluation
        String idCheckEvaluationResult = "NA";

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode resultsNode = mapper.readTree(jsonString).get("results");
            JsonNode itemsNode = resultsNode.get("items");
            if (itemsNode.isArray()) {
                for (JsonNode objNode : itemsNode) {
                    String itemType = objNode.get("type").textValue();
                    if (itemType.equalsIgnoreCase("RA")) {
                        idCheckEvaluationResult = objNode.get("result").textValue();
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("ERROR: " + e);
            e.printStackTrace();
        }

        return idCheckEvaluationResult;
    }

    /**
     * This method gets the decision value from the ID Check review.
     *
     * Note: there can be multiple reviews for a single ID Check. Daon does not prescribe
     *       how customers should use reviews. The business could perform a single review
     *       or they may for instance have 3 reviewers and take the majority decision. For
     *       this reason, it may be more flexible to return results for all reviews and let
     *       the tree or other business logic make the final decision.
     *
     *       For the initial version of this node, we will iterate through all the reviews
     *       and if any of them are APPROVED, we will return APPROVED. This logic should be
     *       reviewed and before using this node.
     *
     *       Possible decision values:
     *          REVIEW_REQUIRED, REVIEW_APPROVED, REVIEW_DECLINED, AUTO_APPROVED, AUTO_DECLINED
     *
     */
    private String getIdCheckReviewDecision(String userId, String idCheckId) {
        String idxBaseUrl = config.identityXBaseUrl();
        String apiKey = config.apiKey();
        String apiPassword = String.valueOf(config.apiPassword());

        RestClient client = new RestClient(idxBaseUrl, apiKey, apiPassword);

        Response response = client.getIdCheckReviews(userId, idCheckId);
        String jsonString = response.readEntity(String.class);

        String idCheckDecision = "NA";

        ObjectMapper mapper = new ObjectMapper();
        try {

            JsonNode userItemsNode = mapper.readTree(jsonString).get("items");
            if (userItemsNode.isArray()) {
                for (JsonNode objNode : userItemsNode) {
                    //idCheckDecision = objNode.get("decision").textValue();
                    String decision = objNode.get("decision").textValue();
                    if (decision.equalsIgnoreCase("AUTO_APPROVED")) {
                        idCheckDecision = "AUTO_APPROVED";
                    } else if (decision.equalsIgnoreCase("REVIEW_APPROVED")) {
                        idCheckDecision = "REVIEW_APPROVED";
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("ERROR: " + e);
            e.printStackTrace();
        }

        return idCheckDecision;
    }

}
