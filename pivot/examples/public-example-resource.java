/**
 * Example Resource demonstrating security annotations usage.
 * 
 * This is a sanitized, generic example that shows architectural patterns
 * without revealing domain-specific business logic.
 * 
 * This file would be placed in the public repository as an example.
 */
package tech.eagledrive.examples.presentation.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.eagledrive.security.presentation.rest.AllowedServices;
import tech.eagledrive.security.presentation.rest.Secured;

/**
 * Example REST resource demonstrating:
 * - @Secured annotation for user authentication
 * - @AllowedServices annotation for service-level authorization
 * - Clean Architecture presentation layer patterns
 */
@Path("/api/examples")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExampleResource
{
    /**
     * Public endpoint - no authentication required.
     * Demonstrates unsecured endpoint pattern.
     */
    @GET
    @Path("/public")
    public Response getPublicData()
    {
        return Response.ok("Public data").build();
    }

    /**
     * Secured endpoint - requires user authentication.
     * Demonstrates @Secured annotation usage.
     */
    @GET
    @Path("/secured")
    @Secured
    public Response getSecuredData()
    {
        // User is automatically authenticated via @Secured annotation
        // Authentication is handled by TokenAuthenticationFilter and
        // UserTokenAuthorizationInterceptor
        return Response.ok("Secured data").build();
    }

    /**
     * Service-only endpoint - requires service authentication.
     * Demonstrates @AllowedServices annotation for service-level authorization.
     */
    @POST
    @Path("/process")
    @Secured
    @AllowedServices({"processing-service"})
    public Response processData(ExampleRequest request)
    {
        // Only processing-service can call this endpoint
        // Service authentication is handled automatically
        return Response.ok("Processed").build();
    }

    /**
     * Multi-service endpoint - allows multiple services.
     * Demonstrates @AllowedServices with multiple service names.
     */
    @GET
    @Path("/shared/{id}")
    @Secured
    @AllowedServices({"service-a", "service-b", "api-gateway"})
    public Response getSharedData(@PathParam("id") String id)
    {
        // Any of the listed services can access this endpoint
        return Response.ok("Shared data for: " + id).build();
    }

    /**
     * Class-level security - all methods in class are secured.
     * Demonstrates applying @Secured at class level.
     */
    @Path("/admin")
    @Secured
    public static class AdminResource
    {
        @GET
        @Path("/users")
        public Response getUsers()
        {
            // All methods in this class require authentication
            return Response.ok("Users list").build();
        }

        @GET
        @Path("/stats")
        public Response getStats()
        {
            // Also requires authentication
            return Response.ok("Statistics").build();
        }
    }
}

/**
 * Example request DTO (generic, non-domain-specific).
 */
record ExampleRequest(String data, String type)
{
}
