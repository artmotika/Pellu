package org.artmotika.solanaconnectorservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.solanaconnectorservice.dto.*;
import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.*;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.AccountInfo;
import org.p2p.solanaj.rpc.types.SignatureStatuses;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${solana.rpc.url}")
    private String rpcUrl;

    @Value("${solana.program.id}")
    private String programIdStr;

    @Value("${solana.admin.private-key:}")
    private String adminPrivateKeyBase58;

    @Value("${solana.program.token-program-id}")
    private String tokenProgramIdStr;

    @Value("${solana.program.associated-token-program-id}")
    private String associatedTokenProgramIdStr;

    @Value("${solana.program.system-program-id}")
    private String systemProgramIdStr;

    @Value("${solana.pda.prefix.registry}")
    private String registryPrefix;

    @Value("${solana.pda.prefix.voting}")
    private String votingPrefix;

    @Value("${solana.pda.prefix.user}")
    private String userPrefix;

    @Value("${solana.pda.prefix.platform-auth}")
    private String platformAuthPrefix;

    @Value("${solana.pda.prefix.ata}")
    private String ataPrefix;

    @Value("${solana.discriminators.create-asset}")
    private byte[] createAssetDiscriminator;

    @Value("${solana.discriminators.toggle-ipo}")
    private byte[] toggleIpoDiscriminator;

    @Value("${solana.discriminators.start-voting}")
    private byte[] startVotingDiscriminator;

    @Value("${solana.discriminators.trade-dfa}")
    private byte[] tradeDfaDiscriminator;

    @Value("${solana.discriminators.register-user}")
    private byte[] registerUserDiscriminator;

    @Value("${solana.discriminators.update-kyc}")
    private byte[] updateKycDiscriminator;

    @Value("${solana.discriminators.clawback}")
    private byte[] clawbackDiscriminator;

    @Value("${solana.discriminators.dividend-payout}")
    private byte[] dividendPayoutDiscriminator;

    private RpcClient rpcClient;
    private final KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;
    private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    private Account adminAccount;
    private PublicKey programId;
    private PublicKey tokenProgramId;
    private PublicKey associatedTokenProgramId;
    private PublicKey systemProgramId;
    
    private final Map<String, PublicKey> assetMintCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.programId = new PublicKey(programIdStr);
        this.tokenProgramId = new PublicKey(tokenProgramIdStr);
        this.associatedTokenProgramId = new PublicKey(associatedTokenProgramIdStr);
        this.systemProgramId = new PublicKey(systemProgramIdStr);

        log.info("Solana Connector initialized connecting to {}", rpcUrl);
        this.rpcClient = new RpcClient(rpcUrl);
        if (adminPrivateKeyBase58 != null && !adminPrivateKeyBase58.isEmpty()) {
            this.adminAccount = new Account(Base58.decode(adminPrivateKeyBase58));
        } else {
            log.warn("No admin private key provided. Using random account (transactions will fail if SOL is needed).");
            this.adminAccount = new Account();
        }
    }

    @KafkaListener(topics = "${kafka.topics.assets-created}", groupId = "${kafka.groups.solana-connector}")
    public void createAssetOnChain(Map<String, Object> asset) {
        String assetId = (String) asset.get("id");
        String name = (String) asset.get("name");
        long totalSupply = ((Number) asset.get("totalSupply")).longValue();
        String mintStr = (String) asset.get("solanaMintAddress");

        log.info("Creating Asset on Solana: {} with Mint: {}", assetId, mintStr);

        PublicKey mint = new PublicKey(mintStr);
        assetMintCache.put(assetId, mint);
        
        PublicKey assetRegistryPda = derivePda(registryPrefix, assetId);

        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(assetRegistryPda, false, true));
        keys.add(new AccountMeta(mint, false, false));
        keys.add(new AccountMeta(adminAccount.getPublicKey(), true, true));
        keys.add(new AccountMeta(systemProgramId, false, false));

        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(createAssetDiscriminator);
        serializeString(buffer, assetId);
        serializeString(buffer, name);
        buffer.putLong(totalSupply);
        serializeString(buffer, (String) asset.getOrDefault("legalDocHash", "MOCK_HASH"));
        buffer.putLong(((Number) asset.getOrDefault("tradeUnlockTimestamp", System.currentTimeMillis() / 1000 + 3600)).longValue());

        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.ipo-status}", groupId = "${kafka.groups.solana-connector}")
    public void toggleIpoOnChain(Map<String, Object> event) {
        String assetId = (String) event.get("assetId");
        boolean active = "IPO_ACTIVE".equals(event.get("status"));
        log.info("Toggling IPO on Solana for {}: {}", assetId, active);
        
        PublicKey assetRegistryPda = derivePda(registryPrefix, assetId);
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(toggleIpoDiscriminator);
        buffer.put((byte) (active ? 1 : 0));
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.vote-started}", groupId = "${kafka.groups.solana-connector}")
    public void startVotingOnChain(VotingEventDto event) {
        String actionId = event.getActionId();
        log.info("Initializing Voting on Solana: {}", actionId);
        
        PublicKey votingPda = derivePda(votingPrefix, actionId);
        PublicKey assetRegistryPda = derivePda(registryPrefix, event.getAssetId());

        List<AccountMeta> keys = List.of(
            new AccountMeta(votingPda, false, true),
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(systemProgramId, false, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(startVotingDiscriminator);
        serializeString(buffer, actionId);
        serializeString(buffer, event.getTitle());
        buffer.put((byte) event.getOptions().size());
        buffer.putLong(System.currentTimeMillis() / 1000 + 86400); 
        
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.orders-validated}", groupId = "${kafka.groups.solana-connector}")
    public void tradeDfa(ValidatedOrderEventDto event) {
        CompletableFuture.runAsync(() -> {
            log.info("Processing Trade on Solana: {}", event.getId());
            
            PublicKey assetRegistryPda = derivePda(registryPrefix, event.getAssetId());
            PublicKey sellerAccountPda = derivePda(userPrefix, event.getSellerWallet());
            PublicKey buyerAccountPda = derivePda(userPrefix, event.getBuyerWallet());
            
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
            buffer.put(tradeDfaDiscriminator);
            buffer.putLong(event.getAmount().longValue());

            TransactionInstruction instr = new TransactionInstruction(programId, keys, buffer.array());
            String signature = sendAndConfirm(instr);
            
            ExecutionResultDto result = new ExecutionResultDto();
            result.setOrderId(event.getId());
            result.setTxHash(signature);
            kafkaTemplate.send("${kafka.topics.trades-executed}", result);
        }, virtualThreadExecutor);
    }

    @KafkaListener(topics = "${kafka.topics.users-registered}", groupId = "${kafka.groups.solana-connector}")
    public void registerUserOnChain(String userWalletStr) {
        PublicKey userWallet = new PublicKey(userWalletStr);
        PublicKey userAccountPda = derivePda(userPrefix, userWalletStr);
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(userAccountPda, false, true),
            new AccountMeta(userWallet, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, true),
            new AccountMeta(systemProgramId, false, false)
        );

        sendAndConfirm(new TransactionInstruction(programId, keys, registerUserDiscriminator));
    }

    @KafkaListener(topics = "${kafka.topics.kyc-updated}", groupId = "${kafka.groups.solana-connector}")
    public void updateKycOnChain(KycUpdateEventDto event) {
        PublicKey assetRegistryPda = derivePda(registryPrefix, event.getAssetId());
        PublicKey targetUserAccountPda = derivePda(userPrefix, event.getUserWallet());
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(targetUserAccountPda, false, true),
            new AccountMeta(new PublicKey(event.getUserWallet()), false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(updateKycDiscriminator);
        buffer.put(event.isApproved() ? (byte)1 : (byte)0);
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.admin-clawback}", groupId = "${kafka.groups.solana-connector}")
    public void clawbackOnChain(ClawbackEventDto event) {
        PublicKey assetRegistryPda = derivePda(registryPrefix, event.getAssetId());
        PublicKey platformAuthPda = derivePdaStatic(platformAuthPrefix);
        
        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(new PublicKey(event.getTargetTokenAccount()), false, true),
            new AccountMeta(new PublicKey(event.getDestinationTokenAccount()), false, true),
            new AccountMeta(platformAuthPda, false, false),
            new AccountMeta(tokenProgramId, false, false)
        );

        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(clawbackDiscriminator);
        buffer.putLong(event.getAmount());
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    @KafkaListener(topics = "${kafka.topics.dividend-payout}", groupId = "${kafka.groups.solana-connector}")
    public void executeDividendPayout(Map<String, Object> event) {
        log.info("Executing Dividend Payout on Solana");
        
        String assetId = (String) event.get("assetId");
        PublicKey assetRegistryPda = derivePda(registryPrefix, assetId);
        
        String userWallet = (String) event.get("userWallet");
        String sourceAccountStr = (String) event.get("sourceTokenAccount");
        
        PublicKey userTokenAccount = deriveAta(userWallet, assetId);
        PublicKey sourceTokenAccount = sourceAccountStr != null ? new PublicKey(sourceAccountStr) : adminAccount.getPublicKey();

        List<AccountMeta> keys = List.of(
            new AccountMeta(assetRegistryPda, false, false),
            new AccountMeta(sourceTokenAccount, false, true),
            new AccountMeta(userTokenAccount, false, true),
            new AccountMeta(adminAccount.getPublicKey(), true, false),
            new AccountMeta(tokenProgramId, false, false)
        );

        long amount = ((Number) event.get("amount")).longValue();
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(dividendPayoutDiscriminator);
        buffer.putLong(amount);
        sendAndConfirm(new TransactionInstruction(programId, keys, buffer.array()));
    }

    private String sendAndConfirm(TransactionInstruction instr) {
        try {
            Transaction tx = new Transaction();
            tx.addInstruction(instr);
            
            // Get recent blockhash
            String recentBlockhash = rpcClient.getApi().getRecentBlockhash();
            tx.setRecentBlockHash(recentBlockhash);
            
            String sig = rpcClient.getApi().sendTransaction(tx, adminAccount);
            log.info("Transaction sent: {}", sig);

            // Wait for confirmation
            boolean confirmed = false;
            for (int i = 0; i < 60; i++) {
                Thread.sleep(1000);
                SignatureStatuses statuses = rpcClient.getApi().getSignatureStatuses(List.of(sig), true);
                if (statuses != null && statuses.getValue() != null && !statuses.getValue().isEmpty()) {
                    SignatureStatuses.Value status = statuses.getValue().get(0);
                    if (status != null) {
                        String confirmationStatus = status.getConfirmationStatus();
                        if ("confirmed".equals(confirmationStatus) || "finalized".equals(confirmationStatus)) {
                            confirmed = true;
                            log.info("Transaction {} confirmed with status {}", sig, confirmationStatus);
                            break;
                        }
                    }
                }
            }
            if (!confirmed) {
                log.warn("Transaction {} not confirmed within 60s timeout", sig);
            }
            return sig;
        } catch (Exception e) {
            log.error("Solana transaction failed", e);
            return "ERROR_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private PublicKey deriveAta(String wallet, String assetId) {
        PublicKey mint = assetMintCache.get(assetId);
        if (mint == null) {
            mint = fetchMintFromChain(assetId);
            if (mint != null) {
                assetMintCache.put(assetId, mint);
            } else {
                log.error("Could not find mint for assetId: {}. Fallback to PDA.", assetId);
                return derivePda(ataPrefix, wallet + assetId);
            }
        }
        return getAssociatedTokenAddress(new PublicKey(wallet), mint);
    }

    private PublicKey getAssociatedTokenAddress(PublicKey owner, PublicKey mint) {
        try {
            return PublicKey.findProgramAddress(
                List.of(owner.toByteArray(), tokenProgramId.toByteArray(), mint.toByteArray()),
                associatedTokenProgramId
            ).getAddress();
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive ATA", e);
        }
    }

    private PublicKey fetchMintFromChain(String assetId) {
        try {
            PublicKey registryPda = derivePda(registryPrefix, assetId);
            AccountInfo accountInfo = rpcClient.getApi().getAccountInfo(registryPda);
            if (accountInfo == null || accountInfo.getValue() == null) {
                return null;
            }
            
            List<String> dataList = accountInfo.getValue().getData();
            if (dataList == null || dataList.isEmpty()) {
                return null;
            }
            
            // Anchor data is Base64 encoded in the first element of the list
            byte[] data = Base64.getDecoder().decode(dataList.get(0));
            // Anchor account structure: 8 bytes discriminator + fields
            // AssetRegistry: admin(32) + compliance(32) + mint(32)
            // Mint starts at offset 8 + 32 + 32 = 72
            if (data.length < 104) return null;
            
            byte[] mintBytes = Arrays.copyOfRange(data, 72, 104);
            return new PublicKey(mintBytes);
        } catch (Exception e) {
            log.error("Failed to fetch mint from chain for assetId: {}", assetId, e);
            return null;
        }
    }

    private PublicKey derivePda(String prefix, String seed) {
        try {
            byte[] seedBytes;
            if (userPrefix.equals(prefix)) {
                // For user accounts, seed is the wallet public key bytes
                seedBytes = Base58.decode(seed);
            } else {
                // For registry and voting, seed is the ID string bytes
                seedBytes = seed.getBytes();
            }
            return PublicKey.findProgramAddress(List.of(prefix.getBytes(), seedBytes), programId).getAddress();
        } catch (Exception e) {
            log.error("Failed to derive PDA for prefix {} and seed {}", prefix, seed, e);
            return systemProgramId;
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
