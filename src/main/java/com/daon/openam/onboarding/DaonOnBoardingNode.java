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

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.sm.annotations.adapters.Password;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.ws.rs.core.Response;

@Node.Metadata(outcomeProvider = DaonOnBoardingNode.DaonOnBoardingOutcomeProvider.class,
        configClass = DaonOnBoardingNode.Config.class)
public class DaonOnBoardingNode extends AbstractDecisionNode {

    private static final String BUNDLE = "com/daon/openam/onboarding/DaonOnBoardingNode";
    private final Logger logger = LoggerFactory.getLogger("amAuth");
    private final Config config;

    /**
     * Configuration for the node.
     */
    public interface Config {
        /**
         * base URL for the IdentityX server
         *
         * @return the URL
         */
        @Attribute(order = 100, validators = {RequiredValueValidator.class})
        String identityXBaseUrl();

        /**
         * the api key for API access
         *
         * @return the api key
         */
        @Attribute(order = 200, validators = {RequiredValueValidator.class})
        String apiKey();

        /**
         * password for the api key
         *
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
     */
    @Inject
    public DaonOnBoardingNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        List<TextInputCallback> textInputCallbackList = context.getCallbacks(TextInputCallback.class);

        if (context.hasCallbacks() && (textInputCallbackList.size()) > 0) {
            String idCheckIdVal = "";
            String userIdVal = "";

            for (TextInputCallback tic : textInputCallbackList) {
                if (tic.getPrompt().equalsIgnoreCase("Please provide the IdCheckId")) {
                    idCheckIdVal = tic.getText();
                } else if (tic.getPrompt().equalsIgnoreCase("Please provide the userID")) {
                    userIdVal = tic.getText();
                }

            }
            return getIdCheckReviewDecision(userIdVal, idCheckIdVal).build();
        }

        return Action.send(new ArrayList<Callback>() {{
            add(new TextOutputCallback(TextOutputCallback.INFORMATION, "Please provide IdCheckId"));
            add(new TextInputCallback("Please provide the IdCheckId", "idcheck.id"));
            add(new TextOutputCallback(TextOutputCallback.INFORMATION, "Please provide UserId"));
            add(new TextInputCallback("Please provide the userID", "user.id"));
        }}).build();
    }

    /**
     * This method gets the result value from the evaluation.
     * <p>
     * Note that this will only be present if a rules set is applied in the
     * evaluationPolicy. For this reason, it may be better to just check the
     * decision value of the review.
     */
    //TODO Not used
    private String getIdCheckResult(String userId, String idCheckId) throws NodeProcessException {
        RestClient client = new RestClient(config.identityXBaseUrl(), config.apiKey(),
                                           String.valueOf(config.apiPassword()));

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
            logger.error("ERROR: " + e);
            throw new NodeProcessException(e);
        }

        return idCheckEvaluationResult;
    }

    /**
     * This method gets the decision value from the ID Check review.
     * <p>
     * Note: there can be multiple reviews for a single ID Check. Daon does not prescribe
     * how customers should use reviews. The business could perform a single review
     * or they may for instance have 3 reviewers and take the majority decision. For
     * this reason, it may be more flexible to return results for all reviews and let
     * the tree or other business logic make the final decision.
     * <p>
     * For the initial version of this node, we will iterate through all the reviews
     * and if any of them are APPROVED, we will return APPROVED. This logic should be
     * reviewed and before using this node.
     * <p>
     * Possible decision values:
     * REVIEW_REQUIRED, REVIEW_APPROVED, REVIEW_DECLINED, AUTO_APPROVED, AUTO_DECLINED
     *
     * @return Action
     */
    private Action.ActionBuilder getIdCheckReviewDecision(String userId, String idCheckId) throws NodeProcessException {

        String jsonString = new RestClient(config.identityXBaseUrl(), config.apiKey(),
                                           String.valueOf(config.apiPassword())).getIdCheckReviews(userId, idCheckId)
                                                                                .readEntity(String.class);
        JsonNode userItemsNode;
        try {
            userItemsNode = new ObjectMapper().readTree(jsonString).get("items");
        } catch (IOException e) {
            logger.debug("ERROR: " + e);
            throw new NodeProcessException(e);
        }

        if (!userItemsNode.isArray()) {
            throw new NodeProcessException("Unexpected return value type from Daon. Expecting an array of decisions");
        }

        int approved = 0;
        int declined = 0;
        for (JsonNode objNode : userItemsNode) {
            String decision = objNode.get("decision").textValue();
            if ("REVIEW_REQUIRED".equalsIgnoreCase(decision)) {
                return Action.goTo(DaonOnBoardingOutcome.PENDING.name());
            } else if ("AUTO_APPROVED".equalsIgnoreCase(decision) || "REVIEW_APPROVED".equalsIgnoreCase(decision)) {
                approved += 1;
            } else if ("REVIEW_DECLINED".equalsIgnoreCase(decision) || "AUTO_DECLINED".equalsIgnoreCase(decision)) {
                declined += 1;
            }
        }

        if (approved > 0 && declined == 0) {
            return Action.goTo(DaonOnBoardingOutcome.TRUE.name());
        } else if (declined > 0 && approved > 0) {
            return Action.goTo(DaonOnBoardingOutcome.MIXED.name());
        }
        return Action.goTo(DaonOnBoardingOutcome.FALSE.name());

    }

    /**
     * The possible outcomes for the LdapDecisionNode.
     */
    public enum DaonOnBoardingOutcome {
        /**
         * The review is successful.
         */
        TRUE,
        /**
         * The review failed.
         */
        FALSE,
        /**
         * The review status is mixed
         */
        MIXED,
        /**
         * The review status is pending.
         */
        PENDING
    }

    /**
     * Defines the possible outcomes from this Ldap node.
     */
    public static class DaonOnBoardingOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(DaonOnBoardingNode.BUNDLE,
                                                                       DaonOnBoardingOutcomeProvider.class
                                                                               .getClassLoader());
            return ImmutableList.of(
                    new Outcome(DaonOnBoardingOutcome.TRUE.name(), bundle.getString("trueOutcome")),
                    new Outcome(DaonOnBoardingOutcome.FALSE.name(), bundle.getString("falseOutcome")),
                    new Outcome(DaonOnBoardingOutcome.MIXED.name(), bundle.getString("mixedOutcome")),
                    new Outcome(DaonOnBoardingOutcome.PENDING.name(), bundle.getString("pendingOutcome")));
        }
    }

}
