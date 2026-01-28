package com.novibe.common.config;

import java.util.Objects;

public class EnvironmentVariables {

    public static final String DNS = Objects.requireNonNull(System.getenv("DNS"));

    public static final String CLIENT_ID = System.getenv("CLIENT_ID");

    public static final String AUTH_SECRET = System.getenv("AUTH_SECRET");

    public static final String BLOCK = System.getenv("BLOCK");

    public static final String REDIRECT = System.getenv("REDIRECT");

    public static final String EXTERNAL_IP = System.getenv("EXTERNAL_IP");

    public static final String FORCE_REWRITE = System.getenv("FORCE_REWRITE");

}
