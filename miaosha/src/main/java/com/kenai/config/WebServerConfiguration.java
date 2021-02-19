package com.kenai.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

// 当spring容器中没有TomcatEmbeddedServletContainerFactory这个bean时，会把此bean加载进spring容器中
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // 使用对应工厂类提供给我们的接口定制化我们的tomcat connector
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(connector -> {
            Http11NioProtocol protocol = (Http11NioProtocol)connector.getProtocolHandler();
            // 定制化keepalivetimeout,30s内没有请求，自动断开keepalive连接。 默认15s
            protocol.setKeepAliveTimeout(30000);
            // 定制化MaxKeepAliveRequests，当客户端发送超过10000个请求，则自动断开keepalive连接. 默认100
            protocol.setMaxKeepAliveRequests(10000);
        });
    }
}
