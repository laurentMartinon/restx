package samplest;

import com.google.common.base.Optional;
import restx.server.WebServer;
import restx.server.simple.simple.SimpleWebServer;

/**
 * Date: 1/12/13
 * Time: 14:18
 */
public class AppServer {
    public static void main(String[] args) throws Exception {
        int port = Integer.valueOf(Optional.fromNullable(System.getenv("PORT")).or("8080"));
        WebServer server = SimpleWebServer.builder().setRouterPath("/api").setPort(port).build();

        /*
         * load mode from system property if defined, or default to dev
         * be careful with that setting, if you use this class to launch your server in production, make sure to launch
         * it with -Drestx.mode=prod or change the default here
         */
        System.setProperty("restx.mode", System.getProperty("restx.mode", "dev"));

        server.startAndAwait();
    }
}
