package fr.openent.moodle.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import org.entcore.common.controller.ControllerHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.moodleConfig;

public class HttpClientHelper extends ControllerHelper {


    public HttpClientHelper() {
        super();
    }

    /**
     * Create default HttpClient
     * @return new HttpClient
     */
    public static HttpClient createHttpClient(Vertx vertx, JsonObject config) {
        boolean setSsl = true;
        try {
            setSsl = "https".equals(new URI(config.getString("address_moodle")).getScheme());
        } catch (URISyntaxException e) {
            log.error("Invalid moodle uri",e);
        }
        final HttpClientOptions options = new HttpClientOptions();
        options.setSsl(setSsl);
        options.setTrustAll(true);
        options.setVerifyHost(false);
        if (System.getProperty("httpclient.proxyHost") != null) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(System.getProperty("httpclient.proxyHost"))
                    .setPort(Integer.parseInt(System.getProperty("httpclient.proxyPort")))
                    .setUsername(System.getProperty("httpclient.proxyUsername"))
                    .setPassword(System.getProperty("httpclient.proxyPassword"));
            options.setProxyOptions(proxyOptions);
        }
        int maxPoolSize = config.getInteger("http-client-max-pool-size", 0);
        if(maxPoolSize > 0) {
            options.setMaxPoolSize(maxPoolSize);
        }
        return vertx.createHttpClient(options);
    }

    public static void webServiceMoodlePost(JsonObject shareSend, String moodleUrl, HttpClient httpClient,
                                            AtomicBoolean responseIsSent, Handler<Either<String, Buffer>> handler, boolean closeHttpClient) {
        URI url;
        try {
            url = new URI(moodleUrl);
        } catch (URISyntaxException e) {
            handler.handle(new Either.Left<>("Bad request"));
            return;
        }

        final HttpClientRequest httpClientRequest = httpClient.postAbs(url.toString(), response -> {
            if (response.statusCode() == 200) {
                final Buffer buff = Buffer.buffer();
                response.handler(buff::appendBuffer);
                response.endHandler(end -> {
                    handler.handle(new Either.Right<>(buff));
                    if (closeHttpClient) {
                        if (!responseIsSent.getAndSet(true)) {
                            httpClient.close();
                        }
                    }
                });
            } else {
                log.error("fail to post webservice" + response.statusMessage());
                response.bodyHandler(event -> {
                    log.error("Returning body after POST CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                    if (closeHttpClient) {
                        if (!responseIsSent.getAndSet(true)) {
                            httpClient.close();
                        }
                    }
                });
            }
        });

        if (shareSend != null) {
            httpClientRequest.setChunked(true);

            Object parameters = shareSend.getMap().get("parameters");
            String encodedParameters;
            if (parameters instanceof JsonObject) {
                encodedParameters = ((JsonObject) parameters).encode();
            } else if (parameters instanceof JsonArray) {
                encodedParameters = ((JsonArray) parameters).encode();
            } else {
                log.error("unknow parameters format");
                handler.handle(new Either.Left<>("unknow parameters format"));
                return;
            }

            httpClientRequest.write("parameters=").write(encodedParameters);
            httpClientRequest.write("&wstoken=").write(shareSend.getString("wstoken"));
            httpClientRequest.write("&wsfunction=").write(shareSend.getString("wsfunction"));
            httpClientRequest.write("&moodlewsrestformat=").write(shareSend.getString("moodlewsrestformat"));
        }

        httpClientRequest.putHeader("Host", moodleConfig.getString("header"));
        httpClientRequest.putHeader("Content-type", "application/x-www-form-urlencoded");
        //Typically an unresolved Address, a timeout about connection or response
        httpClientRequest.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                log.error(event.getMessage(), event);
                if (!responseIsSent.getAndSet(true)) {
                    handle(event);
                    httpClient.close();
                }
            }
        }).setFollowRedirects(true).end();
    }

    //TODO Finir le helper de WS Get
    public void webServiceMoodleGet (String moodleUrl, Buffer wsResponse, HttpClient httpClient, AtomicBoolean responseIsSent, Handler<Either<String, Buffer>> handler) {

        final HttpClientRequest httpClientRequest = httpClient.getAbs(moodleUrl, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 200) {
                    response.handler(wsResponse::appendBuffer);
                    response.endHandler(end -> {
                        handler.handle(new Either.Right<>(wsResponse));
                        if (!responseIsSent.getAndSet(true)) {
                            httpClient.close();
                        }
                    });
                } else {
                    log.error("fail to call create course webservice" + response.statusMessage());
                    response.bodyHandler(event -> {
                        log.error("Returning body after PT CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                        if (!responseIsSent.getAndSet(true)) {
                            httpClient.close();
                        }
                    });
                    handle(response);
                }
            }
        });
        httpClientRequest.headers().set("Content-Length", "0");
        //Typically an unresolved Address, a timeout about connection or response
        httpClientRequest.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                log.error(event.getMessage(), event);
                if (!responseIsSent.getAndSet(true)) {
                    handle(event);
                    httpClient.close();
                }
            }
        }).end();
    }
}
