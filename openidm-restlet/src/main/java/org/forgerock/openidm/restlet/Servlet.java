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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 */

package org.forgerock.openidm.restlet;

// Java SE
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Java Servlet
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// OSGi
import org.forgerock.openidm.http.ContextRegistrator;
import org.ops4j.pax.web.extender.whiteboard.ExtenderConstants;
import org.ops4j.pax.web.extender.whiteboard.ServletMapping;
import org.ops4j.pax.web.extender.whiteboard.runtime.DefaultServletMapping;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

// Felix SCR
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;

// SLF4J
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Restlet
import org.restlet.Request;
import org.restlet.Restlet;

// JSON Fluent
import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;

// JSON Resource
import org.forgerock.json.resource.JsonResource;
import org.forgerock.json.resource.JsonResourceException;

// JSON Resource Restlet
import org.forgerock.json.resource.restlet.JsonResourceRestlet;

// Restlet Utilities
import org.forgerock.restlet.RestletRouterServlet;

import org.forgerock.openidm.filterregistration.ServletFilterRegistrator;
import org.forgerock.openidm.scope.ScopeFactory;
import org.forgerock.openidm.objset.ObjectSetContext;
import org.forgerock.openidm.script.Script;
import org.forgerock.openidm.script.ScriptException;
import org.forgerock.openidm.script.ScriptThrownException;
import org.forgerock.openidm.script.Scripts;

/**
 * Servlet to handle the REST interface.
 *
 * @author Paul C. Bryan
 * @author aegloff
 */
@Component(
    name = "org.forgerock.openidm.restlet",
    immediate = true,
    policy = ConfigurationPolicy.IGNORE
)
@Reference(
        name = "reference_Servlet_Restlet",
        referenceInterface = Restlet.class,
        bind = "bindRestlet",
        unbind = "unbindRestlet",
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        strategy = ReferenceStrategy.EVENT
)
public class Servlet extends RestletRouterServlet {

    private static final long serialVersionUID = 1L;
    
    /** TODO: Description. */
    final static Logger logger = LoggerFactory.getLogger(Servlet.class);
    
    /** TODO: Description. */
    private static final String PATH_PROPERTY = "openidm.restlet.path";

    /** TODO: Description. */
    private ComponentContext context;

    /** TODO: Description. */
    private ServiceRegistration serviceRegistration;
    
    // Optional scripts to augment/populate the security context
    List<Script> augmentSecurityContext = new CopyOnWriteArrayList<Script>();

    /**
     * Provides automatic binding of {@link Restlet} objects that include the
     * {@code openidm.restlet.path} property.
     */
    protected synchronized void bindRestlet(Restlet restlet, Map<String, Object> properties) {
        Object path = properties.get(PATH_PROPERTY);
        if (path != null && path instanceof String) { // service is specified as internally routable
            attach((String)path, restlet);
        }
    }
    protected synchronized void unbindRestlet(Restlet restlet, Map<String, Object> properties) {
        Object path = properties.get(PATH_PROPERTY);
        if (path != null && path instanceof String) { // service is specified as internally routable
            detach(restlet);
        }
    }

    /**
     * Provides automatic binding of {@code JsonResource} objects that include the
     * {@code openidm.restlet.path} property.
     */
    @Reference(
        name = "reference_Servlet_JsonResource",
        referenceInterface = JsonResource.class,
        bind = "bindJsonResource",
        unbind = "unbindJsonResource",
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        strategy = ReferenceStrategy.EVENT
    )
    protected HashMap<JsonResource, Restlet> restlets = new HashMap<JsonResource, Restlet>();
    protected synchronized void bindJsonResource(JsonResource resource, Map<String, Object> properties) {
        Restlet restlet = new CustomRestlet(resource);
        restlets.put(resource, restlet);
        bindRestlet(restlet, properties);
    }
    protected synchronized void unbindJsonResource(JsonResource resource, Map<String, Object> properties) {
        Restlet restlet = restlets.get(resource);
        if (restlet != null) {
            unbindRestlet(restlet, properties);
            restlets.remove(resource);
        }
    }

    // Register script extensions configured
    @Reference(
        name = "reference_Servlet_ServletFilterRegistrator",
        referenceInterface = ServletFilterRegistrator.class,
        bind = "bindRegistrator",
        unbind = "unbindRegistrator",
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
        policy = ReferencePolicy.DYNAMIC,
        strategy = ReferenceStrategy.EVENT
    )
    protected Map<ServletFilterRegistrator, Script> filterRegistratorMap = 
            new HashMap<ServletFilterRegistrator, Script>();
    protected synchronized void bindRegistrator(ServletFilterRegistrator registrator, 
            Map<String, Object> properties) {
        JsonValue regConfig = registrator.getConfiguration();
        JsonValue scriptCfg = regConfig.get("scriptExtensions").get("augmentSecurityContext");
        if (!scriptCfg.isNull()) {
            Script augmentScript = Scripts.newInstance("Servlet", scriptCfg);
            filterRegistratorMap.put(registrator, augmentScript);
            augmentSecurityContext.add(augmentScript);
            logger.debug("Registered script {}", augmentScript);
        }
    }
    
    protected synchronized void unbindRegistrator(ServletFilterRegistrator registrator, 
            Map<String, Object> properties) {
        Script augmentScript = filterRegistratorMap.remove(registrator);
        if (augmentScript != null) {
            augmentSecurityContext.remove(augmentScript);
            logger.debug("Deregistered script {}", augmentScript);
        }
    }

    @Reference
    public ScopeFactory scopeFactory;

    private Map<String, Object> newScope() {
        return scopeFactory.newInstance(ObjectSetContext.get());
    }

    @Activate
    protected synchronized void activate(ComponentContext context) throws ServletException, NamespaceException {
        this.context = context;
        JsonValue config = new JsonValue(new HashMap());

        String alias = "/openidm";
        DefaultServletMapping servletMapping = new DefaultServletMapping();
        servletMapping.setHttpContextId("openidm");
        servletMapping.setAlias(alias);
        servletMapping.setServlet(this);
        servletMapping.setServletName("OpenIDM REST");
        //All WebApplication elements must be registered with the same BundleContext
        serviceRegistration = FrameworkUtil.getBundle(ContextRegistrator.class).getBundleContext().registerService(ServletMapping.class.getName(), servletMapping, null);
        logger.debug("Registered servlet at {}", alias);
    }

    @Deactivate
    protected synchronized void deactivate(ComponentContext context) {
        if (null != serviceRegistration) {
            serviceRegistration.unregister();
        }
        this.context = null;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        ObjectSetContext.clear(); // start with a fresh slate
        try {
            super.service(request, response);
        } finally {
            ObjectSetContext.clear(); // leave with a fresh slate
        }
    }

    private class CustomRestlet extends JsonResourceRestlet {
        private static final String USERID_ID = "id";
        public static final String USERID_COMPONENT = "component";
        public static final String OPENIDM_ROLES = "openidm-roles";
        public static final String USERID = "userid";
        public static final String USERNAME = "username";

        public CustomRestlet(JsonResource resource) {
            super(resource);
        }
        @Override public JsonValue newContext(Request request) {
            JsonValue result = super.newContext(request);
            JsonValue security = result.get("security");

            JsonValue attrs = new JsonValue(request.getAttributes());
            try {
                JsonValue paramUserName = singleEntry(attrs, "openidm.username");
                JsonValue paramUserResource = singleEntry(attrs, "openidm.resource");
                JsonValue paramUserLocalId = singleEntry(attrs, "openidm.userid");
                JsonValue paramRoles = listEntry(attrs, "openidm.roles");
                JsonValue authInvoked = singleEntry(attrs, "openidm.authinvoked");

                // There must be at least the authInvoked param set to indicate that 
                // the security filter has protected the call
                if (!authInvoked.isNull()) {
                    // If not passed in the principal, user name must be supplied in params
                    if (security.get(USERNAME).isNull()) {
                        logger.debug("username not populated from principal, try to get from params {}", security);
                        security.put(USERNAME, paramUserName.required().asString());
                    }
                    Map<String, Object> qualifiedId = new LinkedHashMap<String, Object>();
                    if (!paramUserResource.isNull()) {
                        qualifiedId.put(USERID_COMPONENT, paramUserResource.asString());
                    }
                    if (!paramUserLocalId.isNull()) {
                        qualifiedId.put(USERID_ID, paramUserLocalId.asString());
                    }
                    if (!paramUserResource.isNull() || !paramUserLocalId.isNull()) {
                        security.put(USERID, qualifiedId);
                    }
                    if (!paramRoles.isNull()) {
                        security.put(OPENIDM_ROLES, paramRoles.asList());
                    }

                    // Invoke augment script if defined
                    if (augmentSecurityContext != null && augmentSecurityContext.size() > 0) {
                        for (Script augmentScript : augmentSecurityContext) {
                            augmentContext(augmentScript, request, security);
                        }
                    }

                    // Check security context fully populated
                    security.get(USERNAME).required();
                    security.get(USERID).required();
                    security.get(USERID).get(USERID_COMPONENT).required();
                    security.get(USERID).get(USERID_ID).required();
                    security.get(OPENIDM_ROLES).required();
                } else {
                    logger.warn("Rejecting invocation as required context to allow invocation not populated");
                    throw new RuntimeException("Rejecting invocation as required context to allow invocation not populated");
                }
            } catch (JsonValueException ex) {
                logger.warn("Security context not populated correctly: {}", ex.getMessage(), ex);
                throw ex;
            } catch (JsonResourceException ex) {
                logger.warn("Failure in augmenting security context: {}", ex.getMessage(), ex);
                throw new RuntimeException("Failure in augmenting security context: " 
                            + ex.getMessage(), ex);
            }

            logger.debug("New populated context: {}", result);
            return result;
        }
        
        private void augmentContext(Script augmentScript, Request request, JsonValue securityContext) throws JsonResourceException {
            // Pass request and current security context to augmeet
            Map<String, Object> scope = newScope();

            try {            
                scope.put("request", request);
                scope.put("security", securityContext.getObject());
                Object ret = augmentScript.exec(scope);
            } catch (ScriptThrownException ste) {
                throw ste.toJsonResourceException(null);
            } catch (ScriptException se) {
                throw se.toJsonResourceException("Failure in executing security context augment script: "
                        + se.getMessage());
            } 
        }

        /**
         * Gets a single entry from attributes at the given key
         * For String, Number, Boolean value these are returned as is
         * For List or Set, the first entry is returned
         * For other types, null is returned
         * @param attributes the list of attributes
         * @param key the attribute key
         * @return the single entry, wrapped in JsonValue convenience wrapper
         */
        private JsonValue singleEntry(JsonValue attributes, String key) {
            JsonValue value = attributes.get(key);
            if (value.isString() || value.isNumber() || value.isBoolean()) {
                return value;
            } else if (value.isList()){
                if (value.size() > 1) {
                    logger.warn("Expecting only one paramter in {} List parameter, passed {}",
                            key, value.size());
                }
                return value.get(0); // Returns null if list was empty
            } else {
                // JsonValue doesn't contain convenience functions for Set yet
                Object rawValue = value.getObject();
                if (rawValue instanceof Set) {
                    Iterator iter = ((Set) rawValue).iterator();
                    if (iter.hasNext()) {
                        Object firstEntry = iter.next();
                        if (iter.hasNext()) {
                            logger.warn("Expecting only one paramter in {} Set parameter, passed {}",
                                    key, value.size());
                        }
                        return new JsonValue(firstEntry);
                    }
                }
            }
            return new JsonValue(null, new JsonPointer(key));
        }

        /**
         * Gets a List from attributes at the given key
         * For a value of type List, it is returned as is
         * For a value of type Set, it is converted and returned as List
         * @param attributes the list of attributes
         * @param key the attribute key
         * @return the List entry, wrapped in JsonValue convenience wrapper
         */
        private JsonValue listEntry(JsonValue attributes, String key) {
            JsonValue value = attributes.get(key);
            if (value.isList()){
                return value;
            } else {
                // JsonValue doesn't contain convenience functions for Set yet
                Object rawValue = value.getObject();
                if (rawValue instanceof Set) {
                    return new JsonValue(new ArrayList<Object>((Set)rawValue));
                }
            }
            return new JsonValue(null, new JsonPointer(key));
        }
    }
}
