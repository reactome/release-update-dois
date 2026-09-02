package org.reactome.release.updateDOIs;

import org.gk.persistence.MySQLAdaptor;

import java.sql.SQLException;
import java.util.Properties;

public class PropertyManager {
    private Properties configProperties;

    public PropertyManager(Properties configProperties) {
        this.configProperties = configProperties;
    }

    public MySQLAdaptor getReleaseDbAdaptor() {
        final String prefix = "release.database.";

        String host = getConfigProperties().getProperty(prefix + "host", "localhost");
        String dbName = getConfigProperties().getProperty(prefix + "dbName");
        String userName = getConfigProperties().getProperty(prefix + "user");
        String password = getConfigProperties().getProperty(prefix + "password");
        int port = Integer.parseInt(getConfigProperties().getProperty(prefix + "port", "3306"));

        try {
            return new MySQLAdaptor(
                host,
                dbName,
                userName,
                password,
                port
            );
        } catch (SQLException e) {
            throw new RuntimeException("Unable to create MySQLAdaptor for " + dbName + "@" + host, e);
        }
    }

    public String getCuratorHostURL() {
        return getConfigProperties().getProperty("curator.hostURL");
    }

    public String getCuratorUserName() {
        return getConfigProperties().getProperty("curator.username");
    }

    public String getCuratorPassword() {
        return getConfigProperties().getProperty("curator.password");
    }

    public int getReleaseNumber() {
        return Integer.parseInt(getConfigProperties().getProperty("releaseNumber"));
    }

    public long getPersonId() {
        return Long.parseLong(getConfigProperties().getProperty("personId"));
    }

    public boolean getTestMode() {
        return Boolean.parseBoolean(getConfigProperties().getProperty("testMode", "true"));
    }

    private Properties getConfigProperties() {
        return this.configProperties;
    }
}
