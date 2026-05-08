package org.artmotika.solanaconnectorservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "solana")
public class SolanaProperties {
    private String rpcUrl;
    private Program program;
    private Admin admin;
    private Pda pda;
    private Map<String, List<Integer>> discriminators;

    @Data
    public static class Program {
        private String id;
        private String tokenProgramId;
        private String associatedTokenProgramId;
        private String systemProgramId;
    }

    @Data
    public static class Admin {
        private String privateKey;
    }

    @Data
    public static class Pda {
        private Map<String, String> prefix;
    }
}
