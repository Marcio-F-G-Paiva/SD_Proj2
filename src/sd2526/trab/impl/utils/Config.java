package sd2526.trab.impl.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class Config {
    private static final Logger Log = Logger.getLogger(Config.class.getName());
    private static final String PROPERTIES_FILE = "messages.props";
    private static Properties properties;

    static {
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(fis);
            Log.info("Loaded properties from " + PROPERTIES_FILE);
        } catch (IOException e) {
            Log.severe("Could not load properties from " + PROPERTIES_FILE + ": " + e.getMessage());
            // Exit or throw a runtime exception if configuration is critical
            System.exit(1); 
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}