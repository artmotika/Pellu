package org.artmotika.solanaconnectorservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.solanaconnectorservice.dto.*;
import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
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

    @KafkaListener(topics = "orders.validated", groupId = "solana-connector-group")
    public void tradeDfa(ValidatedOrderEventDto event) {
        CompletableFuture.runAsync(() -> {
            log.info("Processing Trade on Solana (Async): {}", event.getId());
            byte[] discriminator = { (byte)0xec, (byte)0x85, 0x73, (byte)0xfc, (byte)0xc6, 0x1e, 0x48, (byte)0xf4 };
            ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(discriminator);
            buffer.putLong(event.getAmount().longValue());

            TransactionInstruction instr = new TransactionInstruction(programId, Collections.emptyList(), buffer.array());
            String signature = sendAndConfirm(instr);
            
            ExecutionResultDto result = new ExecutionResultDto();
            result.setOrderId(event.getId());
            result.setTxHash(signature);
            kafkaTemplate.send("trades.executed", result);
        }, virtualThreadExecutor);
    }

    @KafkaListener(topics = "users.registered", groupId = "solana-connector-group")
    public void registerUserOnChain(String userId) {
        CompletableFuture.runAsync(() -> {
            log.info("Registering User on Solana (Async): {}", userId);
            byte[] discriminator = { (byte)0x8e, (byte)0x6e, (byte)0x97, (byte)0x01, (byte)0xd8, (byte)0xab, (byte)0xe9, 0x72 };
            sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), discriminator));
        }, virtualThreadExecutor);
    }

    @KafkaListener(topics = "kyc.updated", groupId = "solana-connector-group")
    public void updateKycOnChain(KycUpdateEventDto event) {
        log.info("Updating KYC on Solana for User: {} -> {}", event.getUserId(), event.isApproved());
        byte[] discriminator = { (byte)0xcb, (byte)0xc6, (byte)0xb0, (byte)0x91, (byte)0xc4, (byte)0x44, 0x33, 0x3e };
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.put(event.isApproved() ? (byte)1 : (byte)0);
        sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), buffer.array()));
    }

    @KafkaListener(topics = "aml.frozen", groupId = "solana-connector-group")
    public void toggleFreezeOnChain(FreezeAccountEventDto event) {
        log.info("Toggling Freeze on Solana for User: {} -> {}", event.getUserId(), event.isFreeze());
        byte[] discriminator = { 0x19, (byte)0xb1, 0x2f, 0x56, (byte)0xcb, (byte)0xbd, 0x2c, (byte)0xc2 };
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.put(event.isFreeze() ? (byte)1 : (byte)0);
        sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), buffer.array()));
    }

    @KafkaListener(topics = "admin.clawback", groupId = "solana-connector-group")
    public void clawbackOnChain(ClawbackEventDto event) {
        log.info("Executing Clawback on Solana: {} -> {} (Amount: {})", event.getTargetWallet(), event.getDestinationWallet(), event.getAmount());
        byte[] discriminator = { (byte)0xff, (byte)0xa8, 0x34, (byte)0x9d, (byte)0x76, (byte)0xc1, (byte)0xf0, (byte)0xa7 };
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(event.getAmount());
        sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), buffer.array()));
    }

    @KafkaListener(topics = "dividend.payout", groupId = "solana-connector-group")
    public void executeDividendPayout(Map<String, Object> event) {
        log.info("Executing Dividend Payout on Solana for User: {}", event.get("userId"));
        byte[] discriminator = { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
        long amount = ((Number) event.get("amount")).longValue();
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(amount);
        sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), buffer.array()));
    }

    private String sendAndConfirm(TransactionInstruction instr) {
        try {
            Transaction tx = new Transaction();
            tx.addInstruction(instr);
            String sig = rpcClient.getApi().sendTransaction(tx, adminAccount);
            
            // Wait for confirmation (simplified for template)
            Thread.sleep(1000); 
            return sig;
        } catch (Exception e) {
            log.error("Solana transaction failed", e);
            return "ERROR_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
