package restx.server;

import com.google.common.base.Throwables;
import com.google.common.eventbus.EventBus;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.common.MoreFiles;
import restx.common.MoreIO;
import restx.common.Version;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicLong;

import static restx.common.MoreFiles.checkFileExists;
import static restx.common.MoreIO.checkCanOpenSocket;

/**
 * TomcatWebServer allow to run embedded tomcat. But its startup time is much slower than JettyWebServer.
 */
public class TomcatWebServer implements WebServer {
    private static final AtomicLong SERVER_ID = new AtomicLong();

    private final static Logger logger = LoggerFactory.getLogger(TomcatWebServer.class);

    private final Tomcat tomcat;
    private final String appBase;
    private final int port;
    private String serverId;
    private final Context context;

    public TomcatWebServer(String appBase, int port) throws ServletException {
        checkFileExists(appBase);
        this.appBase = appBase;
        this.port = port;
        this.serverId = "Tomcat#" + SERVER_ID.incrementAndGet();
        tomcat = new Tomcat();
        tomcat.setPort(port);

        tomcat.setBaseDir(".");
        tomcat.getHost().setAppBase(".");

        String contextPath = "/";

        // Add AprLifecycleListener
        StandardServer server = (StandardServer) tomcat.getServer();
        AprLifecycleListener listener = new AprLifecycleListener();
        server.addLifecycleListener(listener);

        context = tomcat.addWebapp(contextPath, appBase);
    }

    /**
     * Sets the serverId used by this server.
     *
     * Must not be called when server is started.
     *
     * The serverId is used to uniquely identify the main Factory used by REST main router in this server.
     * It allows to access the Factory with Factory.getInstance(serverId).
     *
     * @param serverId the server id to set. Must be unique in the JVM.
     *
     * @return current server
     */
    public synchronized TomcatWebServer setServerId(final String serverId) {
        if (isStarted()) {
            throw new IllegalStateException("can't set server id when server is started");
        }
        this.serverId = serverId;
        return this;
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String baseUrl() {
        return WebServers.baseUri("localhost", port);
    }

    @Override
    public String getServerType() {
        return "Apache Tomcat " + Version.getVersion("org.apache.tomcat", "tomcat-catalina") + ", embedded";
    }

    @Override
    public synchronized void start() throws LifecycleException {
        context.getServletContext().setInitParameter("restx.baseServerUri", baseUrl());
        context.getServletContext().setInitParameter("restx.serverId", serverId);
        checkCanOpenSocket(port);
        WebServers.register(this);
        tomcat.start();
    }

    @Override
    public void startAndAwait() throws LifecycleException {
        start();
        await();
    }

    @Override
    public void await() {
        tomcat.getServer().await();
    }


    public synchronized void stop() throws LifecycleException {
        tomcat.stop();
        WebServers.unregister(serverId);
    }

    @Override
    public synchronized boolean isStarted() {
        return WebServers.getServerById(serverId).isPresent();
    }

    public static WebServerSupplier tomcatWebServerSupplier(final String appBase) {
        return new WebServerSupplier() {
            @Override
            public WebServer newWebServer(int port) {
                try {
                    return new TomcatWebServer(appBase, port);
                } catch (ServletException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
    }

}
