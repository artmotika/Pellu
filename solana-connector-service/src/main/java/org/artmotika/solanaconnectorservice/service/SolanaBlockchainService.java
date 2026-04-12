package org.artmotika.solanaconnectorservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.solanaconnectorservice.dto.*;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.types.SignatureStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolanaBlockchainService {
    private final RpcClient rpcClient = new RpcClient("https://api.devnet.solana.com");
    private final KafkaTemplate<String, ExecutionResultDto> kafkaTemplate;
    
    // Platform Admin Wallet (Mocking a real key for non-mocking transaction flow)
    private final Account adminAccount = new Account(); 
    private final PublicKey programId = new PublicKey("DfaPlatform22222222222222222222222222222222");

    @KafkaListener(topics = "orders.validated", groupId = "solana-connector-group")
    public void tradeDfa(ValidatedOrderEventDto event) {
        log.info("Executing Trade DFA for Order: {}", event.getId());
        // Instruction Data: discriminator (8 bytes) + amount (u64)
        byte[] discriminator = { (byte)0xec, (byte)0x85, 0x73, (byte)0xfc, (byte)0xc6, 0x1e, 0x48, (byte)0xf4 };
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(event.getAmount().longValue());

        // Account list would normally include Mint, User Accounts, PDA, etc.
        // Forbrevity, using dummy PKs but real Transaction building
        TransactionInstruction instr = new TransactionInstruction(
                programId,
                Collections.emptyList(), // Mocking AccountMeta list for brevity in this complex example
                buffer.array()
        );

        String signature = sendAndConfirm(instr);
        
        ExecutionResultDto result = new ExecutionResultDto();
        result.setOrderId(event.getId());
        result.setTxHash(signature);
        kafkaTemplate.send("trades.executed", result);
    }

    @KafkaListener(topics = "users.registered", groupId = "solana-connector-group")
    public void registerUserOnChain(String userId) {
        log.info("Registering User on Solana: {}", userId);
        byte[] discriminator = { (byte)0x8e, 0x6e, (byte)0x97, 0x01, (byte)0xd8, (byte)0xab, (byte)0xe9, 0x72 };
        TransactionInstruction instr = new TransactionInstruction(programId, Collections.emptyList(), discriminator);
        sendAndConfirm(instr);
    }

    @KafkaListener(topics = "kyc.updated", groupId = "solana-connector-group")
    public void updateKycOnChain(KycUpdateEventDto event) {
        log.info("Updating KYC on Solana for User: {} -> {}", event.getUserId(), event.isApproved());
        byte[] discriminator = { (byte)0xcb, (byte)0xc6, (byte)0xb0, (byte)0x91, (byte)0xc4, 0x44, 0x33, 0x3e };
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
        byte[] discriminator = { (byte)0xff, (byte)0xa8, 0x34, (byte)0x9d, 0x76, (byte)0xc1, (byte)0xf0, (byte)0xa7 };
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(discriminator);
        buffer.putLong(event.getAmount());
        sendAndConfirm(new TransactionInstruction(programId, Collections.emptyList(), buffer.array()));
    }

    private String sendAndConfirm(TransactionInstruction instr) {
        try {
            Transaction tx = new Transaction();
            tx.addInstruction(instr);
            String sig = rpcClient.getApi().sendTransaction(tx, adminAccount);
            
            boolean confirmed = false;
            while (!confirmed) {
                SignatureStatus status = rpcClient.getApi().getSignatureStatuses(List.of(sig), true).get(0);
                if (status != null && "finalized".equals(status.getConfirmationStatus())) {
                    confirmed = true;
                } else {
                    Thread.sleep(2000);
                }
            }
            return sig;
        } catch (Exception e) {
            log.error("Solana transaction failed", e);
            return "ERROR_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
