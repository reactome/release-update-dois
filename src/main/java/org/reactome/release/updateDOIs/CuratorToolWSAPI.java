package org.reactome.release.updateDOIs;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.reactome.curation.model.InstanceList;
import org.reactome.curation.model.SimpleInstance;
import org.reactome.curation.user.model.User;
import org.reactome.server.graph.domain.model.Person;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 * Created 9/18/2025
 */
public class CuratorToolWSAPI {
    private static final String HOST_URL = "http://localhost:9090/api/";
    private static final String AUTH_URL = HOST_URL + "auth/login";
    private static final String FIND_BY_DB_ID = HOST_URL + "curation/findByDbId/";
    private static final String FIND_DB_OBJ_BY_DB_ID = HOST_URL + "curation/findDatabaseObjectByDbId/";
    private static final String FIND_DB_OBJS_BY_DB_IDS = HOST_URL + "curation/findByDbIds/";
    private static final String SEARCH_INSTANCES = HOST_URL + "curation/searchInstances/";
    private static final String COMMIT_URL = HOST_URL + "curation/commit";

    private String jwtToken;

    public CuratorToolWSAPI() {
        this.jwtToken = this.fetchJwtToken("test", "password");
    }

    public List<SimpleInstance> getPathwaysWithoutDOIs() {
        int skip = 0;
        int limit = 1000;

        Integer total = null;

        List<SimpleInstance> pathwaysWithoutDOIs = new ArrayList<>();
        do {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                URI uri = new URIBuilder(SEARCH_INSTANCES + "Pathway/" + skip + "/" + limit)
                    .setCharset(StandardCharsets.UTF_8)
                    .addParameter("attributes", "doi")
                    .addParameter("operands", "regex")
                    .addParameter("searchKeys", "(?!10\\.3180).*")   // raw regex; URIBuilder encodes it
                    .build();

                HttpGet request = new HttpGet(uri);
                request.setHeader("Accept", "application/json");
                request.setHeader("Authorization", "Bearer " + getJwtToken());

                HttpResponse response = httpClient.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RuntimeException("Failed : HTTP error code : " + statusCode);
                }
                String json = EntityUtils.toString(response.getEntity());
                if (json == null || json.isEmpty()) {
                    return null;
                }
                ObjectMapper objectMapper = new ObjectMapper();
                InstanceList instanceList = objectMapper.readValue(json, InstanceList.class);

                pathwaysWithoutDOIs.addAll(instanceList.getInstances());

                if (total == null) {
                    total = instanceList.getTotalCount();
                }
                skip += limit;
            } catch (Exception e) {
                throw new RuntimeException("Error fetching SimpleInstance from API", e);
            }
        } while (skip < total);
        return pathwaysWithoutDOIs.stream().map(pathway -> this.findByDbId(pathway.getDbId())).collect(Collectors.toList());
    }

    public SimpleInstance getPathwayWithDOI(String doi) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            int skip = 0;
            int limit = 1;

            URI uri = new URIBuilder(SEARCH_INSTANCES + "Pathway/" + skip + "/" + limit)
                .setCharset(StandardCharsets.UTF_8)
                .addParameter("attributes", "doi")
                .addParameter("operands", "equal")
                .addParameter("searchKeys", doi)
                .build();

            HttpGet request = new HttpGet(uri);
            request.setHeader("Accept", "application/json");
            request.setHeader("Authorization", "Bearer " + getJwtToken());

            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode);
            }
            String json = EntityUtils.toString(response.getEntity());
            if (json == null || json.isEmpty()) {
                return null;
            }
            ObjectMapper objectMapper = new ObjectMapper();
            InstanceList instanceList = objectMapper.readValue(json, InstanceList.class);

            if (instanceList.isEmpty()) {
                return null;
            }

            return this.findByDbId(instanceList.getInstances().get(0).getDbId());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching SimpleInstance from API", e);
        }
    }

    public SimpleInstance commit(SimpleInstance simpleInstance) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        //	mapper.addMixIn(org.reactome.curation.model.SimpleInstance.class, DatabaseObjectMixin.class);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(COMMIT_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + getJwtToken());

            String simpleInstanceJSON = mapper.writeValueAsString(simpleInstance);

            post.setEntity(new StringEntity(simpleInstanceJSON));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode);
            }

            return mapper.readValue(EntityUtils.toString(response.getEntity()), SimpleInstance.class);
        } catch (IOException e) {
            throw new RuntimeException("Error committing simple instance " + simpleInstance + " to API", e);
        }
    }

    public List<SimpleInstance> findDatabaseObjectsByDbIds(List<Long> dbIds) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(FIND_DB_OBJS_BY_DB_IDS);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Accept", "application/json");
            post.setHeader("Authorization", "Bearer " + getJwtToken());

            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(dbIds);

            post.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();

            String responseBody = response.getEntity() != null
                ? EntityUtils.toString(response.getEntity())
                : "<no body>";

            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode + "\nResponse body: " + responseBody);
            }

            return mapper.readValue(EntityUtils.toString(response.getEntity()), new TypeReference<List<SimpleInstance>>(){});
        } catch (IOException e) {
            throw new RuntimeException("Error retrieving dbIds " + dbIds + " from API: ", e);
        }
    }

    public SimpleInstance findByDbId(long dbId) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(FIND_BY_DB_ID + dbId);
            request.setHeader("Accept", "application/json");
            request.setHeader("Authorization", "Bearer " + getJwtToken());
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode);
            }
            String json = EntityUtils.toString(response.getEntity());
            if (json == null || json.isEmpty()) {
                return null;
            }
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, SimpleInstance.class);
        }
        catch (Exception e) {
            throw new RuntimeException("Error fetching SimpleInstance from API", e);
        }
    }

    public Person fetchPersonInstance(long personDbId) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(FIND_DB_OBJ_BY_DB_ID + personDbId);
            request.setHeader("Accept", "application/json");
            request.setHeader("Authorization", "Bearer " + getJwtToken());
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode);
            }
            String json = EntityUtils.toString(response.getEntity());
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, Person.class);
        }
        catch (Exception e) {
            throw new RuntimeException("Error fetching SimpleInstance from API", e);
        }
    }

    private String fetchJwtToken(String username, String password) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(AUTH_URL);
            post.setHeader("Content-Type", "application/json");
            ObjectMapper mapper = new ObjectMapper();
            String jsonObj = mapper.writeValueAsString(new User(username, password));
            post.setEntity(new StringEntity(jsonObj));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + statusCode);
            }
            String jwt = EntityUtils.toString(response.getEntity());
            if (jwt.startsWith("\"") && jwt.endsWith("\"")) {
                jwt = jwt.substring(1, jwt.length() - 1);
            }
            this.jwtToken = jwt;
            return jwt;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching JWT token from API", e);
        }
    }

    private String getJwtToken() {
        return this.jwtToken;
    }
}