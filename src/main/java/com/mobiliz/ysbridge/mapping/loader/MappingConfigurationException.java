package com.mobiliz.ysbridge.mapping.loader;

/**
 * Raised when a mapping definition cannot be compiled.
 *
 * <p>Always carries the location inside the YAML document, because the whole point of validating at
 * startup is to say exactly which line to fix.
 */
public class MappingConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MappingConfigurationException(String location, String problem) {
        super(location + " — " + problem);
    }

    public MappingConfigurationException(String location, String problem, Throwable cause) {
        super(location + " — " + problem, cause);
    }
}
