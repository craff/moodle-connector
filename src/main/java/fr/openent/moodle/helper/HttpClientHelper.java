package fr.openent.moodle.helper;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.impl.DefaultMoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import fr.openent.moodle.service.MoodleWebService;
import io.vertx.core.net.ProxyOptions;
import org.entcore.common.controller.ControllerHelper;

import java.util.concurrent.atomic.AtomicBoolean;

public class HttpClientHelper extends ControllerHelper {

    private final MoodleWebService moodleWebService;

    public HttpClientHelper() {
        super();
        this.moodleWebService = new DefaultMoodleWebService(Moodle.moodleSchema, "course");
    }

    /**
     * Create default HttpClient
     * @return new HttpClient
     */
    public static HttpClient createHttpClient(Vertx vertx) {
        final HttpClientOptions options = new HttpClientOptions();
        options.setSsl(true);
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
        return vertx.createHttpClient(options);
    }

    public void webServiceMoodlePost(String moodleUrl, HttpClient httpClient, AtomicBoolean responseIsSent, Handler<Either<String, Buffer>> handler) {
        final HttpClientRequest httpClientRequest = httpClient.postAbs(moodleUrl, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 200) {
                    final Buffer buff = Buffer.buffer();
                    response.handler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            buff.appendBuffer(event);
                        }
                    });
                    response.endHandler(new Handler<Void>() {
                        @Override
                        public void handle(Void end) {
                            handler.handle(new Either.Right<>(buff));
                            if (!responseIsSent.getAndSet(true)) {
                                httpClient.close();
                            }
                        }
                    });
                } else {
                    log.debug(response.statusMessage());
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            log.debug("Returning body after PT CALL : " +  moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                            if (!responseIsSent.getAndSet(true)) {
                                httpClient.close();
                            }
                        }
                    });
                }
            }
        });
        httpClientRequest.headers().set("Content-Length", "50").set("Content-Type", "application/json");
        //Typically an unresolved Address, a timeout about connection or response
        httpClientRequest.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                log.debug(event.getMessage(), event);
                if (!responseIsSent.getAndSet(true)) {
                    handle(event);
                    httpClient.close();
                }
            }
        }).end();
    }
}
