package org.artmotika.solanaconnectorservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.solanaconnectorservice.config.SolanaProperties;
import org.artmotika.solanaconnectorservice.dto.*;
import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SignatureStatuses;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolanaBlockchainService {

    private final SolanaProperties solanaProperties;
    private final KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;

    private RpcClient rpcClient;
    private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    private Account adminAccount;
    private PublicKey programId;
    private PublicKey tokenProgramId;
    private PublicKey associatedTokenProgramId;
    private PublicKey systemProgramId;
    
    private final Map<String, PublicKey> assetMintCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.programId = new PublicKey(solanaProperties.getProgram().getId());
        this.tokenProgramId = new PublicKey(solanaProperties.getProgram().getTokenProgramId());
        this.associatedTokenProgramId = new PublicKey(solanaProperties.getProgram().getAssociatedTokenProgramId());
        this.systemProgramId = new PublicKey(solanaProperties.getProgram().getSystemProgramId());

        log.info("Solana Connector initialized connecting to {}", solanaProperties.getRpcUrl());
        this.rpcClient = new RpcClient(solanaProperties.getRpcUrl());
        
        String adminKey = solanaProperties.getAdmin().getPrivateKey();
        if (adminKey != null && !adminKey.isEmpty()) {
            this.adminAccount = new Account(Base58.decode(adminKey));
        } else {
            log.warn("No admin private key provided. Using random account.");
            this.adminAccount = new Account();
        }
    }

    private byte[] getDiscriminator(String key) {
        List<Integer> list = solanaProperties.getDiscriminators().get(key);
        if (list == null) {
            log.error("Missing discriminator property: {}", key);
            return new byte[8];
        }
        byte[] bytes = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            bytes[i] = list.get(i).byteValue();
        }
        return bytes;
    }

    @KafkaListener(topics = "${kafka.topics.assets-created}", groupId = "${kafka.groups.solana-connector}")
    public void createAssetOnChain(AssetDto asset) {
        String assetId = asset.getId();
        log.info("Creating Asset on Solana: {}", assetId);

        PublicKey mint = new PublicKey(asset.getSolanaMintAddress());
        assetMintCache.put(assetId, mint);
        
        PublicKey assetRegistryPda = derivePda("registry", assetId);

        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, true),
            new AccountMeta(mint, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(systemProgramId, false, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("create-asset"));
        serializeString(buffer, assetId);
        serializeString(buffer, asset.getName());
        buffer.putLong(asset.getTotalSupply());
        serializeString(buffer, asset.getLegalDocHash() != null ? asset.getLegalDocHash() : "MOCK_HASH");
        buffer.putLong(asset.getTradeUnlockTimestamp() != null ? asset.getTradeUnlockTimestamp() : 0L);

        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.ipo-status}", groupId = "${kafka.groups.solana-connector}")
    public void toggleIpoOnChain(Map<String, Object> event) {
        String assetId = (String) event.get("assetId");
        boolean active = "IPO_ACTIVE".equals(event.get("status"));
        
        PublicKey assetRegistryPda = derivePda("registry", assetId);
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("toggle-ipo"));
        buffer.put((byte) (active ? 1 : 0));
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.vote-started}", groupId = "${kafka.groups.solana-connector}")
    public void startVotingOnChain(VotingEventDto event) {
        PublicKey votingPda = derivePda("voting", event.getActionId());
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());

        List<AccountMeta> keys = List.of(
            new AccountMeta(votingPda, false, true),
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(systemProgramId, false, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("start-voting"));
        serializeString(buffer, event.getActionId());
        serializeString(buffer, event.getTitle());
        buffer.put((byte) event.getOptions().size());
        buffer.putLong(System.currentTimeMillis() / 1000 + 86400); 
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.orders-validated}", groupId = "${kafka.groups.solana-connector}")
    public void tradeDfa(ValidatedOrderEventDto event) {
        CompletableFuture.runAsync(() -> {
            PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
            PublicKey sellerAccountPda = derivePda("user", event.getSellerWallet());
            PublicKey buyerAccountPda = derivePda("user", event.getBuyerWallet());
            
            PublicKey sellerTokenAccount = event.getSellerTokenAccount() != null ? 
                new PublicKey(event.getSellerTokenAccount()) : deriveAta(event.getSellerWallet(), event.getAssetId());
            PublicKey buyerTokenAccount = event.getBuyerTokenAccount() != null ? 
                new PublicKey(event.getBuyerTokenAccount()) : deriveAta(event.getBuyerWallet(), event.getAssetId());

            List<AccountMeta> keys = List.of(
                new AccountMeta(assetRegistryPda, false, false),
                new AccountMeta(sellerAccountPda, false, false),
                new AccountMeta(buyerAccountPda, false, false),
                new AccountMeta(new PublicKey(event.getBuyerWallet()), false, false),
                new AccountMeta(new PublicKey(event.getSellerWallet()), false, false),
                new AccountMeta(sellerTokenAccount, false, true),
                new AccountMeta(buyerTokenAccount, false, true),
                new AccountMeta(adminAccount.getPublicKey(), true, false),
                new AccountMeta(tokenProgramId, false, false)
            );

            ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(getDiscriminator("trade-dfa"));
            buffer.putLong(event.getAmount().longValue());

            String signature = sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
            
            ExecutionResultDto result = new ExecutionResultDto();
            result.setOrderId(event.getId());
            result.setTxHash(signature);
            kafkaTemplate.send("${kafka.topics.trades-executed}", result);
        }, virtualThreadExecutor);
    }

    @KafkaListener(topics = "${kafka.topics.users-registered}", groupId = "${kafka.groups.solana-connector}")
    public void registerUserOnChain(String userWalletStr) {
        PublicKey userAccountPda = derivePda("user", userWalletStr);
        List<AccountMeta> keys = List.of(
            new AccountMeta(userAccountPda, false, true),
            new AccountMeta(new PublicKey(userWalletStr), false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(systemProgramId, false, false)
        );
        sendAndConfirm(new TransactionInstruction(programId, keys, getDiscriminator("register-user")));
    }

    @KafkaListener(topics = "${kafka.topics.kyc-updated}", groupId = "${kafka.groups.solana-connector}")
    public void updateKycOnChain(KycUpdateEventDto event) {
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
        PublicKey targetUserAccountPda = derivePda("user", event.getUserWallet());
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(targetUserAccountPda, false, true),
            new AccountMeta(new PublicKey(event.getUserWallet()), false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("update-kyc"));
        buffer.put(event.isApproved() ? (byte)1 : (byte)0);
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.admin-clawback}", groupId = "${kafka.groups.solana-connector}")
    public void clawbackOnChain(ClawbackEventDto event) {
        PublicKey assetRegistryPda = derivePda("registry", event.getAssetId());
        PublicKey platformAuthPda = derivePdaStatic("platform_auth");
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(new PublicKey(event.getTargetTokenAccount()), false, true),
            new AccountMeta(new PublicKey(event.getDestinationTokenAccount()), false, true),
            new AccountMeta(platformAuthPda, false, false),
            new AccountMeta(tokenProgramId, false, false)
        );
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("clawback"));
        buffer.putLong(event.getAmount());
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.dividend-payout}", groupId = "${kafka.groups.solana-connector}")
    public void executeDividendPayout(Map<String, Object> event) {
        String assetId = (String) event.get("assetId");
        PublicKey assetRegistryPda = derivePda("registry", assetId);
        PublicKey userTokenAccount = deriveAta((String) event.get("userWallet"), assetId);
        PublicKey sourceTokenAccount = event.get("sourceTokenAccount") != null ? 
            new PublicKey((String) event.get("sourceTokenAccount")) : adminAccount.getPublicKey();

        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(sourceTokenAccount, false, true),
            new AccountMeta(userTokenAccount, false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(tokenProgramId, false, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(getDiscriminator("dividend-payout"));
        buffer.putLong(((Number) event.get("amount")).longValue());
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    private String sendAndConfirm(TransactionInstruction instr) {
        try {
            Transaction tx = new Transaction();
            tx.addInstruction(instr);
            tx.setRecentBlockHash(rpcClient.getApi().getRecentBlockhash());
            String sig = rpcClient.getApi().sendTransaction(tx, adminAccount);
            log.info("Transaction sent: {}", sig);
            return sig;
        } catch (Exception e) {
            log.error("Solana transaction failed", e);
            return "ERROR_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private PublicKey deriveAta(String wallet, String assetId) {
        PublicKey mint = assetMintCache.computeIfAbsent(assetId, this::fetchMintFromChain);
        if (mint == null) return derivePda("ata", wallet + assetId);
        try {
            return PublicKey.findProgramAddress(
                List.of(new PublicKey(wallet).toByteArray(), tokenProgramId.toByteArray(), mint.toByteArray()),
                associatedTokenProgramId
            ).getAddress();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private PublicKey fetchMintFromChain(String assetId) {
        try {
            AccountInfo info = rpcClient.getApi().getAccountInfo(derivePda("registry", assetId));
            if (info == null || info.getValue() == null) return null;
            byte[] data = Base64.getDecoder().decode(info.getValue().getData().get(0));
            return new PublicKey(Arrays.copyOfRange(data, 72, 104));
        } catch (Exception e) { return null; }
    }

    private PublicKey derivePda(String type, String seed) {
        String prefix = solanaProperties.getPda().getPrefix().get(type);
        try {
            byte[] seedBytes = "user".equals(type) ? Base58.decode(seed) : seed.getBytes();
            return PublicKey.findProgramAddress(List.of(prefix.getBytes(), seedBytes), programId).getAddress();
        } catch (Exception e) { return systemProgramId; }
    }

    private PublicKey derivePdaStatic(String type) {
        String prefix = solanaProperties.getPda().getPrefix().get(type);
        try {
            return PublicKey.findProgramAddress(List.of(prefix.getBytes()), programId).getAddress();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void serializeString(ByteBuffer buffer, String s) {
        buffer.putInt(s.length());
        buffer.put(s.getBytes());
    }
}
