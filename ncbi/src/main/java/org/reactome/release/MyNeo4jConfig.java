package org.reactome.release;

import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.SessionFactory;
import org.reactome.server.graph.config.Neo4jConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@org.springframework.context.annotation.Configuration
@ComponentScan( basePackages = {"org.reactome.server.graph"} )
@EnableTransactionManagement
@EnableNeo4jRepositories( basePackages = {"org.reactome.server.graph.repository"} )
@EnableSpringConfigured
public class MyNeo4jConfig extends Neo4jConfig {
    private SessionFactory sessionFactory;

    @Bean
    public Configuration getConfiguration() {
        Configuration config = new Configuration();
        config.driverConfiguration()
                .setDriverClassName("org.neo4j.ogm.drivers.http.driver.HttpDriver")
                .setURI("http://".concat(System.getProperty("neo4j.host")).concat(":").concat(System.getProperty("neo4j.port")))
                .setCredentials(System.getProperty("neo4j.user"),System.getProperty("neo4j.password"));
        return config;
    }

    @Override
    @Bean
    public SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            sessionFactory = new SessionFactory(getConfiguration(), "org.reactome.server.graph.domain" );
        }
        return sessionFactory;
    }
}
