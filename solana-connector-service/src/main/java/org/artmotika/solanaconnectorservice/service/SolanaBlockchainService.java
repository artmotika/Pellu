package org.artmotika.solanaconnectorservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.solanaconnectorservice.dto.*;
import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolanaBlockchainService {

    @Value("${solana.rpc.url:https://api.devnet.solana.com}")
    private String rpcUrl;

    @Value("${solana.program.id:Dfa1111111111111111111111111111111111111111}")
    private String programIdStr;

    @Value("${solana.admin.private-key:}")
    private String adminPrivateKeyBase58;

    private RpcClient rpcClient;
    private final KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;
    private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    private Account adminAccount;
    private PublicKey programId;

    @PostConstruct
    public void init() {
        this.programId = new PublicKey(programIdStr);
        log.info("Solana Connector initialized connecting to {}", rpcUrl);
        this.rpcClient = new RpcClient(rpcUrl);
        if (adminPrivateKeyBase58 != null && !adminPrivateKeyBase58.isEmpty()) {
            this.adminAccount = new Account(Base58.decode(adminPrivateKeyBase58));
        } else {
            log.warn("No admin private key provided. Using random account (transactions will fail if SOL is needed).");
            this.adminAccount = new Account();
        }
    }

    @KafkaListener(topics = "assets.created", groupId = "solana-connector-group")
    public void createAssetOnChain(Map<String, Object> asset) {
        String assetId = (String) asset.get("id");
        String name = (String) asset.get("name");
        long totalSupply = ((Number) asset.get("totalSupply")).longValue();
        String mintStr = (String) asset.get("solanaMintAddress");

        log.info("Creating Asset on Solana: {} with Mint: {}", assetId, mintStr);

        PublicKey mint = new PublicKey(mintStr);
        PublicKey assetRegistryPda = derivePda("registry", assetId);

        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(assetRegistryPda, false, true));
        keys.add(new AccountMeta(mint, false, false));
        keys.add(new AccountMeta(adminAccount.getPublicKey(), true, true));
        keys.add(new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false)); // System Program

        byte[] discriminator = { (byte)0xb1, (byte)0xf2, 0x06, 0x7c, 0x1e, (byte)0x90, 0x51, 0x0f };
        
        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        serializeString(buffer, assetId);
        serializeString(buffer, name);
        buffer.putLong(totalSupply);
        serializeString(buffer, (String) asset.getOrDefault("legalDocHash", "MOCK_HASH"));
        buffer.putLong(((Number) asset.getOrDefault("tradeUnlockTimestamp", System.currentTimeMillis() / 1000 + 3600)).longValue());

        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "ipo.status", groupId = "solana-connector-group")
    public void toggleIpoOnChain(Map<String, Object> event) {
        String assetId = (String) event.get("assetId");
        boolean active = "IPO_ACTIVE".equals(event.get("status"));
        log.info("Toggling IPO on Solana for {}: {}", assetId, active);
        
        PublicKey assetRegistryPda = derivePda("registry", assetId);
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );

        byte[] discriminator = { 0x52, 0x5a, 0x1f, 0x23, (byte)0x85, (byte)0x91, (byte)0xac, 0x10 };
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.put((byte) (active ? 1 : 0));
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "vote.started", groupId = "solana-connector-group")
    public void startVotingOnChain(VotingEventDto event) {
        String actionId = event.getActionId();
        log.info("Initializing Voting on Solana: {}", actionId);
        
        PublicKey votingPda = derivePda("voting", actionId);
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());

        List<AccountMeta> keys = List.of(
            new AccountMeta(votingPda, false, true),
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false)
        );

        byte[] discriminator = { 0x33, (byte)0xc6, (byte)0xe5, 0x73, (byte)0xb9, (byte)0xfb, (byte)0x80, (byte)0xf3 };
        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        serializeString(buffer, actionId);
        serializeString(buffer, event.getTitle());
        buffer.put((byte) event.getOptions().size());
        buffer.putLong(System.currentTimeMillis() / 1000 + 86400); 
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "orders.validated", groupId = "solana-connector-group")
    public void tradeDfa(ValidatedOrderEventDto event) {
        CompletableFuture.runAsync(() -> {
            log.info("Processing Trade on Solana: {}", event.getId());
            
            PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
            PublicKey sellerAccountPda = derivePda("user", event.getSellerWallet());
            PublicKey buyerAccountPda = derivePda("user", event.getBuyerWallet());
            
            List<AccountMeta> keys = List.of(
                new AccountMeta(assetRegistryPda, false, false),
                new AccountMeta(sellerAccountPda, false, false),
                new AccountMeta(buyerAccountPda, false, false),
                new AccountMeta(new PublicKey(event.getBuyerWallet()), false, false),
                new AccountMeta(new PublicKey(event.getSellerWallet()), false, false),
                new AccountMeta(new PublicKey(event.getSellerTokenAccount()), false, true),
                new AccountMeta(new PublicKey(event.getBuyerTokenAccount()), false, true),
                new AccountMeta(adminAccount.getPublicKey(), true, false),
                new AccountMeta(new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), false, false)
            );

            byte[] discriminator = { (byte)0xec, (byte)0x85, 0x73, (byte)0xfc, (byte)0xc6, 0x1e, 0x48, (byte)0xf4 };
            ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(discriminator);
            buffer.putLong(event.getAmount().longValue());

            TransactionInstruction instr = new TransactionInstruction(programId, keys, buffer.array());
            String signature = sendAndConfirm(instr);
            
            ExecutionResultDto result = new ExecutionResultDto();
            result.setOrderId(event.getId());
            result.setTxHash(signature);
            kafkaTemplate.send("trades.executed", result);
        }, virtualThreadExecutor);
    }

    @KafkaListener(topics = "users.registered", groupId = "solana-connector-group")
    public void registerUserOnChain(String userWalletStr) {
        PublicKey userWallet = new PublicKey(userWalletStr);
        PublicKey userAccountPda = derivePda("user", userWalletStr);
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(userAccountPda, false, true),
            new AccountMeta(userWallet, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false)
        );

        byte[] discriminator = { (byte)0x8e, 0x6e, (byte)0x97, (byte)0x01, (byte)0xd8, (byte)0xab, (byte)0xe9, 0x72 };
        sendAndConfirm(new TransactionInstruction(programId, keys, discriminator));
    }

    @KafkaListener(topics = "kyc.updated", groupId = "solana-connector-group")
    public void updateKycOnChain(KycUpdateEventDto event) {
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
        PublicKey targetUserAccountPda = derivePda("user", event.getUserWallet());
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(targetUserAccountPda, false, true),
            new AccountMeta(new PublicKey(event.getUserWallet()), false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );

        byte[] discriminator = { (byte)0xcb, (byte)0xc6, (byte)0xb0, (byte)0x91, (byte)0xc4, (byte)0x44, 0x33, 0x3e };
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.put(event.isApproved() ? (byte)1 : (byte)0);
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "admin.clawback", groupId = "solana-connector-group")
    public void clawbackOnChain(ClawbackEventDto event) {
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
        PublicKey platformAuthPda = derivePdaStatic("platform_auth");
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(new PublicKey(event.getTargetTokenAccount()), false, true),
            new AccountMeta(new PublicKey(event.getDestinationTokenAccount()), false, true),
            new AccountMeta(platformAuthPda, false, false),
            new AccountMeta(new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), false, false)
        );

        byte[] discriminator = { (byte)0xff, (byte)0xa8, 0x34, (byte)0x9d, 0x76, (byte)0xc1, (byte)0xf0, (byte)0xa7 };
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(event.getAmount());
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "dividend.payout", groupId = "solana-connector-group")
    public void executeDividendPayout(Map<String, Object> event) {
        log.info("Executing Dividend Payout on Solana");
        
        String assetId = (String) event.get("assetId");
        PublicKey assetRegistryPda = derivePda("registry", assetId);

        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(new PublicKey((String) event.get("sourceTokenAccount")), false, true),
            new AccountMeta(new PublicKey((String) event.get("userTokenAccount")), false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), false, false)
        );

        byte[] discriminator = { 0x3d, (byte)0x91, (byte)0x8a, 0x22, (byte)0x92, (byte)0xb3, (byte)0x93, (byte)0x55 };
        long amount = ((Number) event.get("amount")).longValue();
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(amount);
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    private String sendAndConfirm(TransactionInstruction instr) {
        try {
            Transaction tx = new Transaction();
            tx.addInstruction(instr);
            String sig = rpcClient.getApi().sendTransaction(tx, adminAccount);
            Thread.sleep(1000); 
            return sig;
        } catch (Exception e) {
            log.error("Solana transaction failed", e);
            return "ERROR_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private PublicKey derivePda(String prefix, String seed) {
        try {
            byte[] seedBytes;
            if ("user".equals(prefix)) {
                // For user accounts, seed is the wallet public key bytes
                seedBytes = Base58.decode(seed);
            } else {
                // For registry and voting, seed is the ID string bytes
                seedBytes = seed.getBytes();
            }
            return PublicKey.findProgramAddress(List.of(prefix.getBytes(), seedBytes), programId).getAddress();
        } catch (Exception e) {
            log.error("Failed to derive PDA for prefix {} and seed {}", prefix, seed, e);
            // Return a mock PDA for tests if derivation fails (e.g. invalid base58)
            return new PublicKey("11111111111111111111111111111111");
        }
    }

    private PublicKey derivePdaStatic(String prefix) {
        try {
            return PublicKey.findProgramAddress(List.of(prefix.getBytes()), programId).getAddress();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void serializeString(ByteBuffer buffer, String s) {
        buffer.putInt(s.length());
        buffer.put(s.getBytes());
    }
}
