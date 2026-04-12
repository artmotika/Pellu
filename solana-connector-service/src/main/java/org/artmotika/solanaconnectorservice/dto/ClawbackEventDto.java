package org.artmotika.solanaconnectorservice.dto;
import lombok.Data;
@Data public class ClawbackEventDto { String targetWallet; String destinationWallet; long amount; }
