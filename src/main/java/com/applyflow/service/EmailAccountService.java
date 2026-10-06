package com.applyflow.service;

import com.applyflow.common.EmailProvider;
import com.applyflow.common.SyncStatus;
import com.applyflow.dto.AccountDtos.ConnectionTestResult;
import com.applyflow.dto.AccountDtos.CreateEmailAccountRequest;
import com.applyflow.dto.AccountDtos.EmailAccountDto;
import com.applyflow.dto.AccountDtos.UpdateEmailAccountRequest;
import com.applyflow.entity.EmailAccount;
import com.applyflow.exception.BadRequestException;
import com.applyflow.exception.ConflictException;
import com.applyflow.exception.FieldValidationException;
import com.applyflow.exception.ImapException;
import com.applyflow.exception.NotFoundException;
import com.applyflow.mail.imap.ImapClient;
import com.applyflow.mail.imap.ImapConnectionSettings;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.persistence.CascadeDeleter;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.security.CredentialEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;

@Service
public class EmailAccountService {

    private static final Logger log = LoggerFactory.getLogger(EmailAccountService.class);

    private final EmailAccountRepository repository;
    private final EmailMessageRepository emailRepository;
    private final ApplicationEventRepository eventRepository;
    private final CredentialEncryptor encryptor;
    private final ImapClient imapClient;
    private final SyncService syncService;
    private final SettingsService settingsService;
    private final DtoMapper mapper;
    private final CascadeDeleter cascade;

    public EmailAccountService(EmailAccountRepository repository, EmailMessageRepository emailRepository,
                               ApplicationEventRepository eventRepository, CredentialEncryptor encryptor,
                               ImapClient imapClient, SyncService syncService, SettingsService settingsService,
                               DtoMapper mapper, CascadeDeleter cascade) {
        this.cascade = cascade;
        this.repository = repository;
        this.emailRepository = emailRepository;
        this.eventRepository = eventRepository;
        this.encryptor = encryptor;
        this.imapClient = imapClient;
        this.syncService = syncService;
        this.settingsService = settingsService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EmailAccountDto> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(mapper::toAccount).toList();
    }

    /** Tests the connection first; saves nothing on failure. Starts the initial sync after commit. */
    @Transactional
    public EmailAccountDto create(CreateEmailAccountRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("The mailbox " + email + " is already connected.");
        }
        EmailProvider provider = req.provider() == null ? EmailProvider.inferFromEmail(email) : req.provider();
        if (provider == EmailProvider.DEMO) {
            throw FieldValidationException.of("provider", "DEMO accounts cannot be created");
        }
        String host = blank(req.host()) ? provider.defaultHost() : req.host().trim();
        if (blank(host)) {
            throw FieldValidationException.of("host", "is required for generic IMAP accounts");
        }
        int port = req.port() == null ? provider.defaultPort() : req.port();
        boolean ssl = req.ssl() == null ? provider.defaultSsl() : req.ssl();
        String username = blank(req.username()) ? email : req.username().trim();
        String folder = blank(req.folder()) ? "INBOX" : req.folder().trim();
        String password = provider == EmailProvider.GMAIL ? req.appPassword().replace(" ", "") : req.appPassword();
        int days = req.initialSyncDays() == null ? settingsService.current().defaultInitialSyncDays()
                : req.initialSyncDays();

        imapClient.testConnection(new ImapConnectionSettings(provider, host, port, ssl, username, password, folder));

        EmailAccount a = new EmailAccount();
        a.setEmail(email);
        a.setProvider(provider);
        a.setHost(host);
        a.setPort(port);
        a.setSsl(ssl);
        a.setUsername(username);
        a.setEncryptedPassword(encryptor.encrypt(password));
        a.setFolder(folder);
        a.setEnabled(true);
        a.setSyncStatus(SyncStatus.CONNECTED);
        a.setInitialSyncDays(days);
        repository.save(a);
        log.info("Connected mailbox {} ({})", email, provider);

        Long id = a.getId();
        afterCommit(() -> {
            try {
                syncService.start(id);
            } catch (RuntimeException e) {
                log.warn("Initial sync for account {} could not be started: {}", id, e.getMessage());
            }
        });
        return mapper.toAccount(a);
    }

    @Transactional
    public EmailAccountDto update(Long id, UpdateEmailAccountRequest req) {
        EmailAccount a = load(id);
        if (a.isDemo() && req.appPassword() != null) {
            throw new BadRequestException("The demo account cannot be changed.");
        }
        boolean connectionChanged = false;
        if (req.appPassword() != null && !req.appPassword().isBlank()) {
            String pw = a.getProvider() == EmailProvider.GMAIL ? req.appPassword().replace(" ", "") : req.appPassword();
            a.setEncryptedPassword(encryptor.encrypt(pw));
            connectionChanged = true;
        }
        if (!blank(req.host())) {
            a.setHost(req.host().trim());
            connectionChanged = true;
        }
        if (req.port() != null) {
            a.setPort(req.port());
            connectionChanged = true;
        }
        if (req.ssl() != null) {
            a.setSsl(req.ssl());
            connectionChanged = true;
        }
        if (!blank(req.username())) {
            a.setUsername(req.username().trim());
            connectionChanged = true;
        }
        if (!blank(req.folder()) && !req.folder().trim().equals(a.getFolder())) {
            a.setFolder(req.folder().trim());
            a.setLastUid(null);
            a.setUidValidity(null);
        }
        if (req.initialSyncDays() != null) {
            a.setInitialSyncDays(req.initialSyncDays());
        }
        if (req.enabled() != null) {
            a.setEnabled(req.enabled());
        }
        if (connectionChanged && a.getSyncStatus() == SyncStatus.ERROR) {
            a.setSyncStatus(SyncStatus.DISCONNECTED);
            a.setLastError(null);
        }
        repository.save(a);
        return mapper.toAccount(a);
    }

    /** Always returns 200 with a result; updates the account status accordingly. */
    @Transactional
    public ConnectionTestResult test(Long id) {
        EmailAccount a = load(id);
        if (a.isDemo()) {
            return new ConnectionTestResult(false, "The demo account is not connected to a real mailbox.");
        }
        try {
            imapClient.testConnection(settings(a));
            if (!syncService.isRunning(id)) {
                a.setSyncStatus(SyncStatus.CONNECTED);
            }
            a.setLastError(null);
            repository.save(a);
            return new ConnectionTestResult(true, "Connection successful.");
        } catch (ImapException e) {
            a.setSyncStatus(SyncStatus.ERROR);
            a.setLastError(e.getMessage());
            repository.save(a);
            return new ConnectionTestResult(false, e.getMessage());
        } catch (IllegalStateException e) {
            String msg = "Stored app password could not be decrypted. Please re-enter it.";
            a.setSyncStatus(SyncStatus.ERROR);
            a.setLastError(msg);
            repository.save(a);
            return new ConnectionTestResult(false, msg);
        }
    }

    @Transactional
    public void clear(Long id) {
        EmailAccount a = load(id);
        if (syncService.isRunning(id)) {
            throw new com.applyflow.exception.SyncInProgressException(a.getEmail());
        }
        eventRepository.deleteForAccountEmails(id);
        emailRepository.deleteByAccountId(id);
        repository.resetCursor(id);
    }

    @Transactional
    public void delete(Long id, boolean purge) {
        EmailAccount a = load(id);
        if (syncService.isRunning(id)) {
            throw new com.applyflow.exception.SyncInProgressException(a.getEmail());
        }
        if (purge) {
            eventRepository.deleteForAccountEmails(id);
            emailRepository.deleteByAccountId(id);
        } else {
            emailRepository.detachFromAccount(id);
        }
        cascade.deleteAccount(a.getId()); // also unlinks its applications and removes its sync jobs
    }

    private EmailAccount load(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Email account", id));
    }

    private ImapConnectionSettings settings(EmailAccount a) {
        return new ImapConnectionSettings(a.getProvider(), a.getHost(), a.getPort(), a.isSsl(), a.getUsername(),
                encryptor.decrypt(a.getEncryptedPassword()), a.getFolder());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
