package com.oneidentity.safeguard.safeguardjava;

import com.oneidentity.safeguard.safeguardjava.authentication.AnonymousAuthenticator;
import com.oneidentity.safeguard.safeguardjava.authentication.CertificateAuthenticator;
import com.oneidentity.safeguard.safeguardjava.authentication.IAuthenticationMechanism;
import com.oneidentity.safeguard.safeguardjava.authentication.PasswordAuthenticator;
import com.oneidentity.safeguard.safeguardjava.data.FullResponse;
import com.oneidentity.safeguard.safeguardjava.data.JsonBody;
import com.oneidentity.safeguard.safeguardjava.data.Method;
import com.oneidentity.safeguard.safeguardjava.data.Service;
import com.oneidentity.safeguard.safeguardjava.event.ISafeguardEventListener;
import com.oneidentity.safeguard.safeguardjava.event.PersistentSafeguardEventListener;
import com.oneidentity.safeguard.safeguardjava.event.SafeguardEventListener;
import com.oneidentity.safeguard.safeguardjava.exceptions.ArgumentException;
import com.oneidentity.safeguard.safeguardjava.exceptions.ObjectDisposedException;
import com.oneidentity.safeguard.safeguardjava.exceptions.SafeguardForJavaException;
import com.oneidentity.safeguard.safeguardjava.restclient.RestClient;
import com.sun.jersey.api.client.ClientResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
//import javax.ws.rs.core.Response;

class SafeguardConnection implements ISafeguardConnection {

    private boolean disposed;

    private final IAuthenticationMechanism authenticationMechanism;

    private final RestClient coreClient;
    private final RestClient applianceClient;
    private final RestClient notificationClient;

    public SafeguardConnection(IAuthenticationMechanism authenticationMechanism) {
        this.authenticationMechanism = authenticationMechanism;

        String safeguardCoreUrl = String.format("https://%s/service/core/v%d",
                this.authenticationMechanism.getNetworkAddress(), this.authenticationMechanism.getApiVersion());
        coreClient = new RestClient(safeguardCoreUrl, authenticationMechanism.isIgnoreSsl());

        String safeguardApplianceUrl = String.format("https://%s/service/appliance/v%d",
                this.authenticationMechanism.getNetworkAddress(), this.authenticationMechanism.getApiVersion());
        applianceClient = new RestClient(safeguardApplianceUrl, authenticationMechanism.isIgnoreSsl());

        String safeguardNotificationUrl = String.format("https://%s/service/notification/v%d",
                this.authenticationMechanism.getNetworkAddress(), this.authenticationMechanism.getApiVersion());
        notificationClient = new RestClient(safeguardNotificationUrl, authenticationMechanism.isIgnoreSsl());
    }

    @Override
    public int getAccessTokenLifetimeRemaining() throws ObjectDisposedException, SafeguardForJavaException {
        if (disposed) {
            throw new ObjectDisposedException("SafeguardConnection");
        }
        int lifetime = authenticationMechanism.getAccessTokenLifetimeRemaining();
        if (lifetime > 0) {
            String msg = String.format("Access token lifetime remaining (in minutes): %d", lifetime);
            Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, msg);
        } else
            Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Access token invalid or server unavailable");
        return lifetime;
    }

    @Override
    public void refreshAccessToken() throws ObjectDisposedException, SafeguardForJavaException {
        if (disposed) {
            throw new ObjectDisposedException("SafeguardConnection");
        }
        authenticationMechanism.refreshAccessToken();
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Successfully obtained a new access token");
    }

    @Override
    public String invokeMethod(Service service, Method method, String relativeUrl, String body,
            Map<String, String> parameters, Map<String, String> additionalHeaders)
            throws ObjectDisposedException, SafeguardForJavaException, ArgumentException {
        if (disposed) {
            throw new ObjectDisposedException("SafeguardConnection");
        }
        return invokeMethodFull(service, method, relativeUrl, body, parameters, additionalHeaders).getBody();
    }

    @Override
    public FullResponse invokeMethodFull(Service service, Method method, String relativeUrl,
            String body, Map<String, String> parameters, Map<String, String> additionalHeaders)
            throws ObjectDisposedException, SafeguardForJavaException, ArgumentException {

        if (disposed) {
            throw new ObjectDisposedException("SafeguardConnection");
        }
        if (Utils.isNullOrEmpty(relativeUrl))
            throw new ArgumentException("Parameter relativeUrl may not be null or empty");
        
        RestClient client = getClientForService(service);
        if (!authenticationMechanism.isAnonymous() && !authenticationMechanism.hasAccessToken()) {
            throw new SafeguardForJavaException("Access token is missing due to log out, you must refresh the access token to invoke a method");
        }
        
        Map<String,String> headers = prepareHeaders(additionalHeaders, service);
//        Response response = null;
        ClientResponse response = null;

        String msg = String.format("Invoking method: %s %s", method.toString().toUpperCase(), client.getBaseURL() + "/" + relativeUrl);
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, msg);
        msg = parameters == null ? "None" : parameters.keySet().stream().map(key -> key + "=" + parameters.get(key)).collect(Collectors.joining(", ", "{", "}"));
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "  Query parameters: {0}", msg);
        msg = headers == null ? "None" : headers.keySet().stream().map(key -> key + "=" + headers.get(key)).collect(Collectors.joining(", ", "{", "}"));
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "  Additional headers: {0}", msg);
        
        switch (method) {
            case Get:
                response = client.execGET(relativeUrl, parameters, headers);
                break;
            case Post:
                response = client.execPOST(relativeUrl, parameters, headers, new JsonBody(body));
                break;
            case Put:
                response = client.execPUT(relativeUrl, parameters, headers, new JsonBody(body));
                break;
            case Delete:
                response = client.execDELETE(relativeUrl, parameters, headers);
                break;
        }
        
        if (response == null) {
            throw new SafeguardForJavaException(String.format("Unable to connect to web service %s", client.getBaseURL()));
        }
        if (!Utils.isSuccessful(response.getStatus())) {
//            String reply = response.readEntity(String.class);
            String reply = response.getEntity(String.class);
            throw new SafeguardForJavaException("Error returned from Safeguard API, Error: "
                    + String.format("%d %s", response.getStatus(), reply));
        }
            
//        FullResponse fullResponse = new FullResponse(response.getStatus(), response.getHeaders(), response.readEntity(String.class));
        FullResponse fullResponse = new FullResponse(response.getStatus(), response.getHeaders(), response.getEntity(String.class));
        
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Reponse status code: {0}", fullResponse.getStatusCode());
        msg = fullResponse.getHeaders() == null ? "None" : fullResponse.getHeaders().keySet().stream().map(key -> key + "=" + fullResponse.getHeaders().get(key)).collect(Collectors.joining(", ", "{", "}"));
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "  Response headers: {0}", msg);
        msg = fullResponse.getBody() == null ? "None" : String.format("%d",fullResponse.getBody().length());
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "  Body size: {0}", msg);
        
        return fullResponse;
    }

    @Override
    public String invokeMethodCsv(Service service, Method method, String relativeUrl, 
            String body, Map<String, String> parameters, Map<String, String> additionalHeaders)
            throws ObjectDisposedException, SafeguardForJavaException, ArgumentException {
        
        if (disposed) {
            throw new ObjectDisposedException("SafeguardConnection");
        }
        if (additionalHeaders == null) {
            additionalHeaders = new HashMap<>();
        }
        additionalHeaders.put("Accept", "text/csv");
        
        return invokeMethodFull(service, method, relativeUrl, body, parameters, additionalHeaders).getBody();
    }
       
    @Override
    public SafeguardEventListener getEventListener() throws ObjectDisposedException, ArgumentException {
        SafeguardEventListener eventListener = new SafeguardEventListener(
                String.format("https://%s/service/event", authenticationMechanism.getNetworkAddress()),
                authenticationMechanism.getAccessToken(), authenticationMechanism.isIgnoreSsl());
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Event listener successfully created for Safeguard connection.");

        return eventListener;
    }

    @Override
    public ISafeguardEventListener getPersistentEventListener()
            throws ObjectDisposedException, SafeguardForJavaException {
        
        if (disposed)
            throw new ObjectDisposedException("SafeguardConnection");

        if ((authenticationMechanism instanceof PasswordAuthenticator) ||
            (authenticationMechanism instanceof CertificateAuthenticator)) {
            return new PersistentSafeguardEventListener((ISafeguardConnection)this.cloneObject());
        }
        throw new SafeguardForJavaException("Unable to create persistent event listener from " + this.authenticationMechanism.getClass().getName());
    }

    @Override
    public void logOut() throws ObjectDisposedException {
        
        if (disposed)
            throw new ObjectDisposedException("SafeguardConnection");
        
        if (!authenticationMechanism.hasAccessToken())
            return;
        try {
            this.invokeMethodFull(Service.Core, Method.Post, "Token/Logout", null, null, null);
            Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Successfully logged out");
        }
        catch (Exception ex) {
            Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Exception occurred during logout", ex);
        }
        authenticationMechanism.clearAccessToken();
        Logger.getLogger(SafeguardConnection.class.getName()).log(Level.FINEST, "Cleared access token");
    }
    
    private RestClient getClientForService(Service service) throws SafeguardForJavaException {
        switch (service) {
            case Core:
                return coreClient;
            case Appliance:
                return applianceClient;
            case Notification:
                return notificationClient;
            case A2A:
                throw new SafeguardForJavaException(
                        "You must call the A2A service using the A2A specific method, Error: Unsupported operation");
            default:
                throw new SafeguardForJavaException("Unknown or unsupported service specified");
        }
    }
    
    private Map<String,String> prepareHeaders(Map<String,String> additionalHeaders, Service service) 
            throws ObjectDisposedException {
        
        Map<String,String> headers = new HashMap<>();
        if (!(authenticationMechanism instanceof AnonymousAuthenticator)) { 
            headers.put("Authorization", String.format("Bearer %s", new String(authenticationMechanism.getAccessToken())));
        }
        
        if (additionalHeaders != null) { 
            headers.putAll(additionalHeaders);
            if (!additionalHeaders.containsKey("Accept"))
                headers.put("Accept", "application/json"); // Assume JSON unless specified
        }
        return headers;
    }

    @Override
    public void dispose()
    {
        if (authenticationMechanism != null)
            authenticationMechanism.dispose();
        disposed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (authenticationMechanism != null)
                authenticationMechanism.dispose();
        } finally {
            disposed = true;
            super.finalize();
        }
    }
    
    public Object cloneObject() throws SafeguardForJavaException 
    {
        return new SafeguardConnection((IAuthenticationMechanism)authenticationMechanism.cloneObject());
    }

}
