package com.intellicampus.audit.service;

import com.intellicampus.audit.dto.AuditLogResponseDto;
import com.intellicampus.audit.dto.AuditVerificationResponseDto;
import com.intellicampus.audit.model.AuditLogEntry;
import com.intellicampus.audit.repository.AuditLogEntryRepository;
import com.intellicampus.audit.validation.HashChainValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditChainService {

    public static final String GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuditLogEntryRepository auditRepository;
    private final HashChainValidator hashChainValidator;

    public AuditChainService(AuditLogEntryRepository auditRepository,
                             HashChainValidator hashChainValidator) {
        this.auditRepository = auditRepository;
        this.hashChainValidator = hashChainValidator;
    }

    public synchronized AuditLogEntry recordEntry(Long actorId,
                                                  String actorName,
                                                  String action,
                                                  String entityType,
                                                  String entityId,
                                                  String oldValue,
                                                  String newValue,
                                                  String reason) {
        String prevHash = auditRepository.findLatestEntry()
                .map(AuditLogEntry::getEntryHash)
                .orElse(GENESIS_PREV_HASH);

        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(ISO_FORMATTER);

        String entryHash = calculateHash(
                prevHash,
                actorId != null ? actorId : 0L,
                action != null ? action : "UNKNOWN",
                entityType != null ? entityType : "GENERIC",
                entityId != null ? entityId : "0",
                oldValue != null ? oldValue : "{}",
                newValue != null ? newValue : "{}",
                reason != null ? reason : "",
                timeStr
        );

        AuditLogEntry entry = new AuditLogEntry(
                prevHash,
                entryHash,
                actorId != null ? actorId : 0L,
                actorName != null ? actorName : "System",
                action,
                entityType,
                entityId,
                oldValue,
                newValue,
                reason
        );
        entry.setCreatedAt(now);

        return auditRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public AuditVerificationResponseDto verifyChain() {
        List<AuditLogEntry> entries = auditRepository.findAllByOrderByIdAsc();
        if (entries.isEmpty()) {
            return AuditVerificationResponseDto.valid(0, GENESIS_PREV_HASH);
        }

        HashChainValidator.ChainValidationResult result = hashChainValidator.validateChain(entries);
        if (result.isValid()) {
            String lastHash = entries.get(entries.size() - 1).getEntryHash();
            return AuditVerificationResponseDto.valid(entries.size(), lastHash);
        } else {
            return AuditVerificationResponseDto.invalid(
                    result.getTotalBlocks(),
                    result.getTamperedIndex(),
                    result.getTamperedEntryId(),
                    result.getMessage()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getChainEntries() {
        return auditRepository.findAllByOrderByIdAsc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getEntriesForEntity(String entityType, String entityId) {
        return auditRepository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public static String calculateHash(String prevHash,
                                       Long actorId,
                                       String action,
                                       String entityType,
                                       String entityId,
                                       String oldValue,
                                       String newValue,
                                       String reason,
                                       String createdAt) {
        String payload = String.format("%s|%d|%s|%s|%s|%s|%s|%s|%s",
                prevHash,
                actorId != null ? actorId : 0L,
                action,
                entityType,
                entityId,
                oldValue,
                newValue,
                reason,
                createdAt
        );

        return sha256Hex(payload);
    }

    public static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private AuditLogResponseDto mapToDto(AuditLogEntry entry) {
        AuditLogResponseDto dto = new AuditLogResponseDto();
        dto.setId(entry.getId());
        dto.setPrevHash(entry.getPrevHash());
        dto.setEntryHash(entry.getEntryHash());
        dto.setActorId(entry.getActorId());
        dto.setActorName(entry.getActorName());
        dto.setAction(entry.getAction());
        dto.setEntityType(entry.getEntityType());
        dto.setEntityId(entry.getEntityId());
        dto.setOldValue(entry.getOldValue());
        dto.setNewValue(entry.getNewValue());
        dto.setReason(entry.getReason());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }
}
