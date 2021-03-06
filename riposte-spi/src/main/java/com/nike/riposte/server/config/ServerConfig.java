package com.nike.riposte.server.config;

import com.nike.backstopper.apierror.ApiError;
import com.nike.backstopper.apierror.projectspecificinfo.ProjectApiErrors;
import com.nike.backstopper.apierror.projectspecificinfo.ProjectSpecificErrorCodeRange;
import com.nike.backstopper.apierror.sample.SampleProjectApiErrorsBase;
import com.nike.backstopper.handler.ApiExceptionHandlerUtils;
import com.nike.backstopper.handler.riposte.config.BackstopperRiposteConfigHelper;
import com.nike.backstopper.service.riposte.BackstopperRiposteValidatorAdapter;
import com.nike.riposte.metrics.MetricsListener;
import com.nike.riposte.server.error.handler.ErrorResponseBodySerializer;
import com.nike.riposte.server.error.handler.RiposteErrorHandler;
import com.nike.riposte.server.error.handler.RiposteUnhandledErrorHandler;
import com.nike.riposte.server.error.validation.RequestSecurityValidator;
import com.nike.riposte.server.error.validation.RequestValidator;
import com.nike.riposte.server.hooks.PipelineCreateHook;
import com.nike.riposte.server.hooks.PostServerStartupHook;
import com.nike.riposte.server.hooks.PreServerStartupHook;
import com.nike.riposte.server.hooks.ServerShutdownHook;
import com.nike.riposte.server.http.Endpoint;
import com.nike.riposte.server.http.RequestInfo;
import com.nike.riposte.server.http.filter.RequestAndResponseFilter;
import com.nike.riposte.server.logging.AccessLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.SSLException;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Interface for providing the configuration options needed by a Riposte server. Reasonable defaults are provided where
 * possible.
 *
 * <p><b>MINIMUM RECOMMENDED IMPLEMENTATION:</b> Although {@link #appEndpoints()} is the only method you are required to
 * implement, the following overrides are highly recommended as a "minimum" implementation for achieving good core
 * application functionality:
 *
 * <ul>
 *     <li>
 *         {@link #riposteErrorHandler()} - Defines the error handler your application will use. The javadocs for this
 *         method explain how to set this up properly for your application.
 *     </li>
 *     <li>
 *         {@link #riposteUnhandledErrorHandler()} - Defines the unhandled error handler your application will use when
 *         {@link #riposteErrorHandler()} fails to handle an error. The javadocs for this method explain how to set this
 *         up properly for your application.
 *     </li>
 *     <li>
 *         {@link #requestContentValidationService()} - Defines the validation service your application will use to
 *         validate incoming request payloads. The javadocs for this method explain how to set this up properly for your
 *         application.
 *     </li>
 *     <li>
 *         {@link #appInfo()} - Defines some metadata about your application, necessary for some other features (e.g.
 *         metrics collection and reporting). The javadocs for this method explain how to set this up properly for your
 *         application.
 *     </li>
 *     <li>
 *         {@link #endpointsPort()}, {@link #endpointsSslPort()}, and/or {@link #isEndpointsUseSsl()} - These determine
 *         which port your application listens on and whether or not it accepts SSL traffic. If you change {@link
 *         #isEndpointsUseSsl()} to return true you will probably also want to override {@link #createSslContext()} so
 *         you're not using a self-signed SSL cert. The defaults for these methods may work as-is for your application.
 *         If so then you do not need to override them.
 *     </li>
 * </ul>
 *
 * @author Nic Munroe
 */
public interface ServerConfig {

    /**
     * @return The {@link Endpoint}s that should be registered for this application.
     */
    Collection<Endpoint<?>> appEndpoints();

    /**
     * @return The list of {@link RequestAndResponseFilter}s that should be applied to requests for this application.
     * These can be thought of as being somewhat similar to Servlet Filters - namely ways to adjust or monitor the
     * requests and responses going through the server outside the confines of endpoints. These filters also contain the
     * capability of short circuiting the request to immediately return a response generated by the filter rather than
     * going through an endpoint (e.g. CORS requests). See the javadocs for {@link RequestAndResponseFilter} for more
     * information on what these filters can do and how to use them. This method can safely return null if you have no
     * filters for your application.
     */
    default List<RequestAndResponseFilter> requestAndResponseFilters() {
        return null;
    }

    /**
     * @return The error handler that should be used for this application for handling known errors. For things that
     * fall through the cracks they will be handled by {@link #riposteUnhandledErrorHandler()}.
     *
     * <p>NOTE: The default implementation returned if you don't override this method is a very basic Backstopper impl -
     * it will not know about your application's {@link ProjectApiErrors} and will instead create a dummy {@link
     * ProjectApiErrors} that only supports the {@link com.nike.backstopper.apierror.sample.SampleCoreApiError} values.
     * This method should be overridden for most real applications to return a real implementation tailored for your
     * app. In practice this usually means copy/pasting this method and simply supplying the correct {@link
     * ProjectApiErrors} for the app. The rest is usually fine for defaults.
     */
    default RiposteErrorHandler riposteErrorHandler() {
        ProjectApiErrors projectApiErrors = new SampleProjectApiErrorsBase() {
            @Override
            protected List<ApiError> getProjectSpecificApiErrors() {
                return null;
            }

            @Override
            protected ProjectSpecificErrorCodeRange getProjectSpecificErrorCodeRange() {
                return null;
            }
        };
        return BackstopperRiposteConfigHelper
            .defaultErrorHandler(projectApiErrors, ApiExceptionHandlerUtils.DEFAULT_IMPL);
    }

    /**
     * @return The error handler that should be used for this application for handling unknown/unexpected/unhandled
     * errors. Known/handled errors will be processed by {@link #riposteErrorHandler()}.
     *
     * <p>NOTE: The default implementation returned if you don't override this method is a very basic Backstopper impl -
     * it will not know about your application's {@link ProjectApiErrors} and will instead create a dummy {@link
     * ProjectApiErrors} that only supports the {@link com.nike.backstopper.apierror.sample.SampleCoreApiError} values.
     * This method should be overridden for most real applications to return a real implementation tailored for your
     * app. In practice this usually means copy/pasting this method and simply supplying the correct {@link
     * ProjectApiErrors} for the app. The rest is usually fine for defaults.
     */
    default RiposteUnhandledErrorHandler riposteUnhandledErrorHandler() {
        ProjectApiErrors projectApiErrors = new SampleProjectApiErrorsBase() {
            @Override
            protected List<ApiError> getProjectSpecificApiErrors() {
                return null;
            }

            @Override
            protected ProjectSpecificErrorCodeRange getProjectSpecificErrorCodeRange() {
                return null;
            }
        };
        return BackstopperRiposteConfigHelper
            .defaultUnhandledErrorHandler(projectApiErrors, ApiExceptionHandlerUtils.DEFAULT_IMPL);
    }

    /**
     * @return The serializer that should be used to convert the error contract bodies returned by {@link
     * #riposteErrorHandler()} and {@link #riposteUnhandledErrorHandler()} when handling errors to a string for sending
     * to the caller. This can safely be null - if this is null then a default serializer will be chosen for you, but if
     * you want custom serialization you can override this method and return whatever you want.
     */
    default ErrorResponseBodySerializer errorResponseBodySerializer() {
        return null;
    }

    /**
     * @return A new self-signed SSL certificate.
     *
     * @throws SSLException
     *     if there is a problem creating the {@link SslContext}.
     * @throws CertificateException
     *     if there is a problem creating the certificate used by {@link SslContext}.
     */
    default SslContext createSslContext() throws SSLException, CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate("localhost");
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    }

    /**
     * @return The {@link RequestValidator} that should be used to validate incoming {@link RequestInfo#getContent()} if
     * desired by a given endpoint. This can safely be null - if this is null then no request content validation will be
     * done automatically (i.e. you would have to do it manually in the endpoint's {@code execute} method).
     * <p/>
     * NOTE: Most apps will simply want to override this to supply a {@link BackstopperRiposteValidatorAdapter}. This is
     * as easy as:
     * <pre>
     *      return new BackstopperRiposteValidatorAdapter(
     *          new ClientDataValidationService(Validation.buildDefaultValidatorFactory().getValidator())
     *      );
     * </pre>
     * You'll need to have a JSR 303 validator implementation in your classpath (e.g. the
     * org.hibernate:hibernate-validator library, which is the reference impl for JSR 303) for that code to work.
     */
    default RequestValidator requestContentValidationService() {
        return null;
    }

    /**
     * @return The default deserializer for incoming request content. This can safely be null - if this is null then a
     * blank empty-constructor {@link ObjectMapper#ObjectMapper()} will be used.
     */
    default ObjectMapper defaultRequestContentDeserializer() {
        return null;
    }

    /**
     * @return The default serializer for outgoing response content. This can safely be null - if this is null then a
     * blank empty-constructor {@link ObjectMapper#ObjectMapper()} will be used.
     */
    default ObjectMapper defaultResponseContentSerializer() {
        return null;
    }

    /**
     * @return true if the server should enable some debugging features, false if those debug features should be hidden.
     * This is usually just for some extra debug logging, but it could result in a significant amount of log spam so it
     * shouldn't be enabled in prod or any other environment where lots of log spam would be a problem.
     * <p/>
     * NOTE: This is different than {@link #isDebugChannelLifecycleLoggingEnabled()}, which enables/disables the {@link
     * io.netty.handler.logging.LoggingHandler} for channel lifecycle and request/response payload info.
     */
    default boolean isDebugActionsEnabled() {
        return false;
    }

    /**
     * @return true if the server should allow {@link io.netty.handler.logging.LoggingHandler}s to be added to the
     * channel pipelines, which gives detailed lifecycle info about the channel (i.e. what local and remote ports it is
     * connected to, when it becomes active/inactive/closed/etc) and request/response payload information. Extremely
     * useful when debugging channel-related problems.
     * <p/>
     * NOTE: This spams a huge amount of data to the logs so it shouldn't be enabled in prod or any other environment
     * where lots of log spam would be a problem.
     */
    default boolean isDebugChannelLifecycleLoggingEnabled() {
        return false;
    }

    /**
     * This non-ssl port will only be used if {@link #isEndpointsUseSsl()} is false, and if so then all traffic must be
     * *non*-SSL normal HTTP traffic (i.e. you can choose HTTP, or HTTPS, but not both).
     */
    default int endpointsPort() {
        return 8080;
    }

    /**
     * This ssl port will only be used if {@link #isEndpointsUseSsl()} is true, and if so then all traffic must be
     * SSL/HTTPS (i.e. you can choose HTTP, or HTTPS, but not both).
     */
    default int endpointsSslPort() {
        return 8443;
    }

    /**
     * @return Whether or not SSL is enabled for the server for endpoints. If this returns true then {@link
     * #endpointsSslPort()} will be used for the application's port, and *all* traffic to the server's endpoints must be
     * SSL/HTTPS. If this returns false then {@link #endpointsPort()} will be used for the application's port, and *all*
     * traffic to the server's endpoints must be *non*-SSL, normal HTTP traffic.
     */
    default boolean isEndpointsUseSsl() {
        return false;
    }

    /**
     * @return The number of netty boss threads to use. This is usually fine at 1.
     */
    default int numBossThreads() {
        return 1;
    }

    /**
     * @return The custom {@link ThreadFactory} you want the Riposte server to use when it creates boss threads, or
     * return null if you want to use the default. Default is recommended unless you have a good reason to override and
     * know what you're doing.
     */
    default ThreadFactory bossThreadFactory() {
        return null;
    }

    /**
     * @return The number of netty I/O worker threads to use. 0 indicates that netty should use the default number of
     * worker threads, which is 2 * [CPU cores in system] and is fine for most purposes.
     */
    default int numWorkerThreads() {
        return 0;
    }

    /**
     * @return The custom {@link ThreadFactory} you want the Riposte server to use when it creates worker threads, or
     * return null if you want to use the default. Default is recommended unless you have a good reason to override and
     * know what you're doing.
     */
    default ThreadFactory workerThreadFactory() {
        return null;
    }

    /**
     * @return The default timeout value for {@link CompletableFuture}s returned by non-blocking endpoints (and for
     * downstream calls when using a proxy/router endpoint). You can override this for your project-wide default, and/or
     * you can override the {@link Endpoint#completableFutureTimeoutOverrideMillis()} method on individual endpoints for
     * a per-endpoint timeout value. This will be used to forcibly cancel any {@link CompletableFuture} (or downstream
     * proxy/router call) that is not done by the time the timeout has passed. This can become necessary if the
     * completable future returned by a non-blocking endpoint is constructed in such a way that it never completes.
     * Having this timeout that cancels rogue completable futures can help prevent memory leaks.
     *
     * <p>As mentioned this value is also used in a similar way for the async downstream calls performed by proxy
     * routing endpoints to make sure they don't hang forever.
     *
     * <p>NOTE: This is just for timing out active requests. If you're looking for timing out idle channels between
     * requests you'll want to adjust {@link #workerChannelIdleTimeoutMillis()} instead.
     */
    default long defaultCompletableFutureTimeoutInMillisForNonblockingEndpoints() {
        // Default to just under 60 seconds so we return a trackable error to the user
        // rather than letting the default AWS ELB timeout squash the connection with a 504 after 60 seconds.
        return 58 * 1000;
    }

    /**
     * @return The amount of time in milliseconds that a worker channel can be idle <b>between requests</b> before it
     * will be closed via {@link io.netty.channel.Channel#close()}. If this argument is less than or equal to 0 then the
     * feature will be disabled and channels will not be closed due to idleness. If you're looking for active request
     * timeouts then you'll want to adjust {@link #defaultCompletableFutureTimeoutInMillisForNonblockingEndpoints()}
     * instead.
     *
     * <p><b>WARNING:</b> turning this feature off is very dangerous because keep-alive connections can potentially stay
     * alive forever. Each connection takes up memory to keep track of the open socket, leading to an out-of-memory type
     * situation if enough clients open keep-alive connections and don't ever close them - something that could easily
     * happen with connection pooling and enough clients. So this should only ever be set to 0 if you *really really*
     * know what you're doing.
     *
     * <p><b>NOTE:</b> This idle timeout only comes into affect between requests, so it will not interfere or conflict
     * with {@link #defaultCompletableFutureTimeoutInMillisForNonblockingEndpoints()}. i.e. Even if an endpoint takes
     * ten times this value to complete it will not be closed due to this idle channel timeout value (since the request
     * is active and the idle channel timeout is only in effect between requests).
     */
    default long workerChannelIdleTimeoutMillis() {
        return 5 * 1000;
    }

    /**
     * @return The amount of time in milliseconds that a proxy/router endpoint should attempt to connect to the
     * downstream service before giving up and throwing a connection timeout exception. Set this to 0 to disable
     * connection timeouts entirely, which is REALLY DEFINITELY NOT RECOMMENDED and you do so at your own risk.
     */
    default long proxyRouterConnectTimeoutMillis() {
        return 10 * 1000;
    }

    /**
     * @return The amount of time in milliseconds that the server should wait without receiving a chunk from the caller
     * once the first chunk has been received but before the last chunk has arrived. If a request has been started (we
     * have received the first chunk of the request) but not yet finished, and this amount of time passes without any
     * further chunks from the caller being received, then a {@link
     * com.nike.riposte.server.error.exception.IncompleteHttpCallTimeoutException} will be thrown and an appropriate
     * error response returned to the caller.
     *
     * <p>If this method returns a value less than or equal to 0 then the incomplete call timeout functionality will
     * be disabled. Be careful with turning this feature off - broken clients or attackers could effectively cause a
     * memory leak by flooding a server with invalid HTTP requests that never complete while keeping the connection
     * open indefinitely.
     */
    default long incompleteHttpCallTimeoutMillis() {
        return 5 * 1000;
    }

    /**
     * @return The maximum allowed number of open channels for the server (incoming channels), with -1 indicating
     * unlimited. Each open channel represents an open socket/connection on the server so this number effectively limits
     * the number of <b>concurrent</b> requests and/or the number of keep-alive connections (whether they are active or
     * not). This is mainly here as a safety-valve against bad actors flooding the server with keep-alive connections.
     * Each socket takes up memory in the OS and the JVM, so if the number of open sockets grows too large it can cause
     * garbage collection thrashing or out-of-memory errors in the JVM, or OS-related problems including but not
     * necessarily limited to the OS killing the JVM process to reclaim memory.
     *
     * <p>If the server detects that the number of open channels is at this threshold then new incoming channels will
     * cause a {@link com.nike.riposte.server.error.exception.TooManyOpenChannelsException} to be thrown at which point
     * the server will respond to the client with a HTTP status 503 and close the new channel.
     *
     * <p>If you see {@link com.nike.riposte.server.error.exception.TooManyOpenChannelsException}s in your server logs
     * and discover that the traffic is legitimate, and the machine you're running on has the memory to handle the extra
     * open connections, then you can safely increase this number. If you're running on a machine without enough memory
     * you may need to lower this number. Setting this to unlimited (-1) leaves the server open to intentional and
     * unintentional DOS style attacks and should only be used in known safe situations.
     */
    default int maxOpenIncomingServerChannels() {
        return 20000;
    }

    /**
     * @return The maximum allowed request size in bytes. If Riposte receives a request larger than this then it will
     * throw a {@link com.nike.riposte.server.error.exception.RequestTooBigException}.
     *
     * This value is an integer, so the max you can set it to is {@link Integer#MAX_VALUE}, which corresponds to 2^31-1,
     * or 2147483647 (around 2 GB).
     *
     * A value of 0 or less is disabling the max request size validation. Defaulting to no limit.
     */
    default int maxRequestSizeInBytes() {
        return 0;
    }

    /**
     * @return The size threshold (in bytes) above which response payloads are eligible for gzip/deflate compression.
     * Compressing small payloads can actually result in a "compressed" payload that is larger than the original and
     * in that case you're using extra CPU for a worse outcome. If a response payload's size is smaller than the
     * byte threshold value returned by this method then it will not be automatically compressed. You can also disable
     * compression on a per-response basis by setting {@link
     * com.nike.riposte.server.http.ResponseInfo#setPreventCompressedOutput(boolean)} to true.
     */
    default int responseCompressionThresholdBytes() {
        return 500;
    }

    /**
     * @return The {@link Executor} that should be used for long running tasks when non-blocking endpoints need to do
     * blocking I/O and there is no nonblocking driver/client, or if the endpoint needs to do serious number crunching
     * or anything else that shouldn't be done on the Netty worker thread. This can be null - if it is null then {@link
     * Executors#newCachedThreadPool()} will be used, which dynamically grows to fulfill demand,
     * reuses threads where possible, and kills threads that have been idle for 60 seconds.
     *
     * <p><b>NOTE:</b> You should try to find a non-blocking solution that uses fixed thread pools rather than use this
     * executor. For example you can use the {@code riposte-async-http-client} (or other async HTTP clients that don't
     * increase thread pool sizes under load) for asynchronous downstream calls that return futures rather than blocking
     * downstream calls, and many databases have non-blocking drivers that work asynchronously via futures. If you want
     * the maximum scalability for your app then this executor (or the default if this is null) should *NEVER* be used.
     */
    default Executor longRunningTaskExecutor() {
        return null;
    }

    /**
     * @return The {@link MetricsListener} that should be used for collecting and reporting Riposte server metrics. This
     * can be null - if it is null then no Riposte server metrics will be collected.
     */
    default MetricsListener metricsListener() {
        return null;
    }

    /**
     * @return The {@link AccessLogger} that should be used to perform access logging. This can be null - if it is null
     * then no access logging will be performed. The default {@link AccessLogger} is fairly robust and extensible, so
     * this method can simply {@code return new AccessLogger();} for many applications.
     */
    default AccessLogger accessLogger() {
        return null;
    }

    /**
     * @return A {@link CompletableFuture} that will eventually return the {@link AppInfo} that should be used to do
     * metrics (and anything else that requires this info). You can return null, or the {@link CompletableFuture} can
     * return null, although some features may fail if you do (e.g. metrics registration).
     *
     * <p>NOTE: Some of this info may come from outside sources and need to be retrieved (e.g. AWS metadata) which is
     * why it's a {@link CompletableFuture}, but if you know at app startup what everything should be you can just
     * return an already-completed {@link CompletableFuture} that contains the correct {@link AppInfo}.
     *
     * <p><b>See the {@code AwsUtil.getAppInfoFutureWithAwsInfo(...)} methods from the {@code riposte-async-http-client}
     * library module for the easy way to return a {@link CompletableFuture} that gets the non-local values from
     * AWS.</b>
     *
     * <p><b>See {@link com.nike.riposte.server.config.impl.AppInfoImpl} for a base implementation and helper methods
     * for creating "local" instances of the {@link AppInfo} interface if you're never going to be deployed in a cloud
     * environment.</b>
     */
    default CompletableFuture<AppInfo> appInfo() {
        return null;
    }

    /**
     * @return The list of {@link PostServerStartupHook} that allows you to implement logic that is automatically
     * executed after the Riposte server is launched. Null is allowed if you have no hooks to execute.
     */
    default List<PostServerStartupHook> postServerStartupHooks() {
        return null;
    }

    /**
     * @return The list of {@link PreServerStartupHook} that allows you to implement logic that is automatically
     * executed before the Riposte server is launched. Null is allowed if you have no hooks to execute.
     */
    default List<PreServerStartupHook> preServerStartupHooks() {
        return null;
    }

    /**
     * @return The list of {@link ServerShutdownHook} that allows you to implement logic that is automatically executed
     * when the Riposte server is shutdown. Null is allowed if you have no hooks to execute.
     */
    default List<ServerShutdownHook> serverShutdownHooks() {
        return null;
    }

    /**
     * @return The list of {@link PipelineCreateHook} that allows you to modify the channel pipeline created by Riposte
     * for handling new channels. Null is allowed if you have no hooks to execute.
     */
    default List<PipelineCreateHook> pipelineCreateHooks() {
        return null;
    }

    /**
     * @return The specific {@link ChannelInitializer} you want used for initializing channel pipelines, or null if you
     * want to use the default. Most of the time the default is fine - only override this if you're sure you know what
     * you want.
     */
    default ChannelInitializer<SocketChannel> customChannelInitializer() {
        return null;
    }

    /**
     * @return The request security validator that should be used before allowing endpoints to execute, or null if you
     * have no security validation to do.
     */
    default RequestSecurityValidator requestSecurityValidator() {
        return null;
    }

    /**
     * @return The list of header keys that should be considered "user ID header keys" for the purposes of distributed
     * tracing, or null if you don't have any user ID header keys. Headers are searched in the given list order, with
     * the first one found winning (in the case where more than one user ID header key was passed at the same time).
     */
    default List<String> userIdHeaderKeys() {
        return null;
    }

    /**
     * @return The {@link HttpRequestDecoderConfig} that should be used when creating the {@link
     * io.netty.handler.codec.http.HttpServerCodec#HttpServerCodec(int, int, int)} handler used to decode incoming
     * bytes into HTTP message objects, or null if you want to use the default values.
     *
     * <p><b>It's recommended that you return null or use the {@link HttpRequestDecoderConfig#DEFAULT_IMPL} unless
     * you're sure you know what you're doing!</b>
     *
     * <p>The default values are 4096 bytes for max initial line length, 8192 bytes for max combined header line length,
     * and 8192 max chunk size. See the javadocs for {@link HttpRequestDecoderConfig} and its methods for more details.
     */
    default HttpRequestDecoderConfig httpRequestDecoderConfig() {
        return null;
    }

    /**
     * Config options that will be used when creating the {@link
     * io.netty.handler.codec.http.HttpRequestDecoder#HttpRequestDecoder(int, int, int)} (or
     * {@link io.netty.handler.codec.http.HttpServerCodec}) handler used to decode incoming bytes into HTTP message
     * objects.
     *
     * <p><b>It's recommended that you use the {@link #DEFAULT_IMPL} unless you're sure you know what you're doing!</b>
     *
     * <p>Please see the javadocs on {@link io.netty.handler.codec.http.HttpRequestDecoder} for full details on these
     * options.
     */
    interface HttpRequestDecoderConfig {

        /**
         * Statically accessible implementation of the {@link HttpRequestDecoderConfig} interface that returns the
         * default values.
         */
        HttpRequestDecoderConfig DEFAULT_IMPL = new HttpRequestDecoderConfig() {};

        /**
         * Defaults to 4096. Please see the javadocs on {@link io.netty.handler.codec.http.HttpRequestDecoder} for full
         * details on this option.
         *
         * @return The maximum allowed length of the initial line (e.g. {@code "GET /some/path HTTP/1.1"}) - if the
         * length of the initial line exceeds this value then an exception will be thrown that will map to an
         * appropriate HTTP status code 400 response.
         */
        default int maxInitialLineLength() {
            return 4096;
        }

        /**
         * Defaults to 8192. Please see the javadocs on {@link io.netty.handler.codec.http.HttpRequestDecoder} for full
         * details on this option.
         *
         * @return The maximum allowed length of all headers combined. If the sum of the length of all headers exceeds
         * this value then an exception will be thrown that will map to an appropriate HTTP status code 431 response.
         */
        default int maxHeaderSize() {
            return 8192;
        }

        /**
         * Defaults to 8192. Please see the javadocs on {@link io.netty.handler.codec.http.HttpRequestDecoder} for full
         * details on this option.
         *
         * @return The maximum length of each chunk of the content - unlike the other options in this interface
         * exceeding this limit does not cause an exception to be thrown, instead it just tells Netty how to chunk
         * the incoming payload. <b>You shouldn't need to adjust this for the vast majority of Riposte projects!</b>
         */
        default int maxChunkSize() {
            return 8192;
        }
    }
}
