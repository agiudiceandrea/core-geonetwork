/*
 * Copyright (C) 2001-2015 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.index.es;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.cluster.Health;
import io.searchbox.core.Bulk;
import io.searchbox.core.BulkResult;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.indices.Analyze;
import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.fao.geonet.utils.Log;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Map;


/**
 * Client to connect to Elasticsearch
 */
public class EsClient implements InitializingBean {
    private static EsClient instance;

    private JestClient client;

    @Value("${es.url}")
    private String serverUrl;

    @Value("${es.username}")
    private String username;

    @Value("${es.password}")
    private String password;

    private boolean activated = false;

    public static EsClient get() {
        return instance;
    }

    public JestClient getClient() throws Exception {
        return client;
    }

    public String getDashboardAppUrl() {
        return dashboardAppUrl;
    }

    public void setDashboardAppUrl(String dashboardAppUrl) {
        this.dashboardAppUrl = dashboardAppUrl;
    }

    @Value("${kb.url}")
    private String dashboardAppUrl;


    @Override
    public void afterPropertiesSet() throws Exception {
        if (StringUtils.isNotEmpty(serverUrl)) {
            JestClientFactory factory = new JestClientFactory();

            if (serverUrl.startsWith("https://")) {
                SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(
                    null, new TrustStrategy() {
                        public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                            return true;
                        }
                    }).build();
                // skip hostname checks
                HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;


                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                SchemeIOSessionStrategy httpsIOSessionStrategy = new SSLIOSessionStrategy(sslContext, hostnameVerifier);
                factory.setHttpClientConfig(new HttpClientConfig
                    .Builder(this.serverUrl)
                    .defaultCredentials(username, password)
                    .multiThreaded(true)
                    .sslSocketFactory(sslSocketFactory) // this only affects sync calls
                    .httpsIOSessionStrategy(httpsIOSessionStrategy) // this only affects async calls
                    .readTimeout(-1)
                    .build());
            } else {
                factory.setHttpClientConfig(new HttpClientConfig
                    .Builder(this.serverUrl)
                    .multiThreaded(true)
                    .readTimeout(-1)
                    .build());
            }
            client = factory.getObject();
            // TODOES Migrate to RestHighLevelClient
//            RestHighLevelClient client = new RestHighLevelClient(
//                RestClient.builder(
//                    new HttpHost("localhost", 9200, "http"),
//                    new HttpHost("localhost", 9201, "http")));

            synchronized (EsClient.class) {
                instance = this;
            }
            activated = true;
        } else {
            Log.debug("geonetwork.index", String.format(
                "No Elasticsearch URL defined '%s'. "
                    + "Check bean configuration. Statistics and dasboard will not be available.", this.serverUrl));
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public EsClient setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public EsClient setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public EsClient setPassword(String password) {
        this.password = password;
        return this;
    }

    public BulkResult bulkRequest(String index, Map<String, String> docs) throws IOException {
        if (!activated) {
            throw new IOException("Index not yet activated.");
        }
        Bulk.Builder bulk = new Bulk.Builder()
            .defaultIndex(index);

        Iterator iterator = docs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry) iterator.next();
            bulk.addAction(
                new Index.Builder(entry.getValue()).id(entry.getKey()).build()
            );
        }
        try {
            BulkResult result = client.execute(bulk.build());
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
//        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
//        BulkResponse response = null;
//        int counter = 0;
//
//        Map<String, String> errors = new HashMap<>();
//        Iterator iterator = docs.keySet().iterator();
//        while (iterator.hasNext()) {
//            String id = (String)iterator.next();
//            try {
//
//                bulkRequestBuilder.add(
//                    client.prepareIndex(index, index, id).setSource(docs.get(id))
//                );
//                counter ++;
//
//                if (bulkRequestBuilder.numberOfActions() % commitInterval == 0) {
//                    response = bulkRequestBuilder.execute().actionGet();
//                    logger.info(String.format(
//                        "Importing %s: %d actions performed. Has errors: %s",
//                        index,
//                        counter,
//                        response.hasFailures()
//                    ));
//                    if (response.hasFailures()) {
//                        errors.put(counter + "", response.buildFailureMessage());
//                        success = false;
//                    }
//                    bulkRequestBuilder = client.prepareBulk();
//                }
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        }
//        if (bulkRequestBuilder.numberOfActions() > 0) {
//            bulkRequestBuilder
//                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
//            response = bulkRequestBuilder.execute().actionGet();
//            logger.info(String.format(
//                "Importing %s: %d actions performed. Has errors: %s",
//                index,
//                counter,
//                response.hasFailures()
//            ));
//            if (response.hasFailures()) {
//                errors.put(counter + "", response.buildFailureMessage());
//                success = false;
//            }
//        }
    }


    public void bulkRequestAsync(Bulk.Builder bulk , JestResultHandler<BulkResult> handler) {
        client.executeAsync(bulk.build(), handler);

    }

    public BulkResult bulkRequestSync(Bulk.Builder bulk) throws IOException {
        return client.execute(bulk.build());
    }


    public JestResult query(String index, String luceneQuery) throws Exception {
        if (!activated) {
            return null;
        }

        // TODOES: Add permission if index is gn-records
        // See EsHTTPProxy#addUserInfo
        String searchQuery = "{\"query\": {\"query_string\": {" +
            "\"query\": \"" + luceneQuery + "\"" +
            "}}}";

        final Search search = new Search.Builder(searchQuery)
            .addIndex(index)
            .build();
        final JestResult result = client.execute(search);
        if (result.isSucceeded()) {
            return result;
        } else {
            throw new IOException(String.format(
                "Error during removal. Errors is '%s'.", result.getErrorMessage()
            ));
        }
    }

    public String deleteByQuery(String index, String query) throws Exception {
        if (!activated) {
            return "";
        }

        String searchQuery = "{\"query\": {\"query_string\": {" +
            "\"query\": \"" + query + "\"" +
            "}}}";

        DeleteByQuery deleteAll = new DeleteByQuery.Builder(searchQuery)
            .addIndex(index)
            .build();
        final JestResult result = client.execute(deleteAll);
        if (result.isSucceeded()) {
            return String.format("Record removed. %s.", result.getJsonString());
        } else {
            throw new IOException(String.format(
                "Error during removal. Errors is '%s'.", result.getErrorMessage()
            ));
        }
    }

    /**
     * Analyze a field and a value against the index
     * or query phase and return the first value generated
     * by the specified analyzer. For now mainly used for
     * synonyms analysis.
     *
     * See https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-analyze.html
     *{
     *   "tokens" : [
     *     {
     *       "token" : "area management/restriction/regulation zones and reporting units",
     *       "start_offset" : 0,
     *       "end_offset" : 64,
     *       "type" : "word",
     *       "position" : 0
     *     }
     *   ]
     * }
     * or when a synonym is found
     * {
     *   "tokens" : [
     *     {
     *       "token" : "elevation",
     *       "start_offset" : 0,
     *       "end_offset" : 8,
     *       "type" : "SYNONYM",
     *       "position" : 0
     *     }
     *   ]
     * }
     * @param fieldValue    The field value to analyze
     *
     * @return The analyzed string value if found or empty text if not found or if an exception occurred.
     */
    public static String analyzeField(String collection,
                                      String analyzer,
                                      String fieldValue) {
        Analyze analyze = new Analyze.Builder()
            .index(collection)
            .analyzer(analyzer)
            // Replace , as it is meaningful in synonym map format
            .text(fieldValue.replaceAll(",", ""))
            .build();
        String analyzedValue = "";
        try {
            JestResult result = EsClient.get().getClient().execute(analyze);

            if (result.isSucceeded()) {
                JsonArray tokens = result.getJsonObject().getAsJsonArray("tokens");
                if (tokens != null && tokens.size() == 1) {
                    JsonObject token = tokens.get(0).getAsJsonObject();
                    String type = token.get("type").getAsString();
                    if ("SYNONYM".equals(type) || "word".equals(type)) {
                        return token.get("token").getAsString();
                    }
                }
                return "";
            } else {
                return "";
            }
        } catch (Exception ex) {
            return "";
        }
    }


    protected void finalize() {
        client.shutdownClient();
    }

    // TODO: check index exist too
    public String getServerStatus() throws Exception {
        JestResult result = getClient().execute(new Health.Builder().build());
        return result.getJsonObject().get("status").getAsString();
    }
}