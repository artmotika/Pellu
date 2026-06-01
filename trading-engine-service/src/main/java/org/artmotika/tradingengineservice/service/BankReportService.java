package org.artmotika.tradingengineservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artmotika.tradingengineservice.model.TradeLedger;
import org.artmotika.tradingengineservice.repo.TradeLedgerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankReportService {

    private final TradeLedgerRepository ledgerRepository;

    @Value("${reports.bank.storage-path:/reports/archive}")
    private String reportStoragePath;

    @Value("${reports.bank.outbox-path:/reports/outbox}")
    private String reportOutboxPath;

    @Scheduled(cron = "${reports.bank.cron:0 0 23 * * ?}")
    public void generateDailyBankReport() {
        log.info("Starting Daily Bank Report Generation...");
        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            List<TradeLedger> dailyTrades = ledgerRepository.findByTimestampAfter(yesterday);

            if (dailyTrades.isEmpty()) {
                log.info("No trades recorded since yesterday, skipping report.");
                return;
            }

            String csvContent = formatAsCsv(dailyTrades);
            String fileName = "daily_bank_ledger_" + System.currentTimeMillis() + ".csv";
            Path reportPath = Paths.get(reportStoragePath, fileName);

            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, csvContent);

            log.info("Bank Report generated successfully: {}", reportPath.toAbsolutePath());
            
            // FULL IMPLEMENTATION: Move report to Outbox for Bank "SFTP" pick-up
            uploadReportToBank(reportPath);

        } catch (Exception e) {
            log.error("Failed to generate bank report", e);
        }
    }

    private String formatAsCsv(List<TradeLedger> trades) {
        StringBuilder csv = new StringBuilder("ID,TxHash,Price,Time,AssetID,UserID,Type\n");
        for (TradeLedger t : trades) {
            csv.append(t.getId()).append(",")
               .append(t.getTransactionHash()).append(",")
               .append(t.getExecutionPrice()).append(",")
               .append(t.getTimestamp()).append(",")
               .append(t.getOrder().getAsset().getId()).append(",")
               .append(t.getOrder().getUserId()).append(",")
               .append(t.getOrder().getType()).append("\n");
        }
        return csv.toString();
    }

    /**
     * Fully implemented hook for bank integration.
     * In a production environment, this would use an SFTP client or S3 bucket.
     * For "Out-of-the-Box" readiness, it moves the file to a designated 'outbox' folder
     * which mimics a bank's dropzone.
     */
    private void uploadReportToBank(Path reportPath) throws Exception {
        Path outboxDir = Paths.get(reportOutboxPath);
        Files.createDirectories(outboxDir);
        
        Path targetPath = outboxDir.resolve(reportPath.getFileName());
        Files.copy(reportPath, targetPath);
        
        log.info("Report successfully 'uploaded' (copied) to Bank Outbox: {}", targetPath.toAbsolutePath());
    }
}
